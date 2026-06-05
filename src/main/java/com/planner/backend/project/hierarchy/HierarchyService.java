package com.planner.backend.project.hierarchy;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HierarchyService {

    private static final Set<String> TIPOS_VALIDOS = Set.of("PRESIDENCIA", "DIRETORIA", "TRIBO", "SQUAD");

    private final FileJsonStore jsonStore;
    private final java.nio.file.Path hierarchyPath;

    public HierarchyService(
            FileJsonStore jsonStore,
            @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.hierarchyPath = java.nio.file.Path.of(dataDir, "hierarchy-nodes.json");
        ensureFile();
    }

    private void ensureFile() {
        try {
            if (Files.exists(hierarchyPath)) return;
            java.nio.file.Path parent = hierarchyPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(hierarchyPath, "[]", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao inicializar arquivo de hierarquia.", ex);
        }
    }

    public List<HierarchyNode> listNodes() throws IOException {
        return loadNodes();
    }

    public HierarchyNode createNode(CreateHierarchyNodeRequest request) throws IOException {
        validate(request);
        List<HierarchyNode> all = new ArrayList<>(loadNodes());
        String parentId = normalizeParent(request.parentId());
        if (parentId != null && all.stream().noneMatch(n -> parentId.equals(n.id())))
            throw new IllegalArgumentException("No pai nao encontrado.");
        int ordem = request.ordem() != null ? request.ordem() : nextOrdem(all, parentId);
        HierarchyNode created = new HierarchyNode(
                UUID.randomUUID().toString(),
                request.tipo().trim().toUpperCase(),
                request.nome().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                parentId,
                ordem,
                sanitizeMembros(request.membros()),
                sanitizeLoIds(request.loIds()),
                OffsetDateTime.now());
        all.add(created);
        saveNodes(all);
        return created;
    }

    public HierarchyNode updateNode(String nodeId, CreateHierarchyNodeRequest request) throws IOException {
        validate(request);
        List<HierarchyNode> all = new ArrayList<>(loadNodes());
        String parentId = normalizeParent(request.parentId());
        if (parentId != null && parentId.equals(nodeId))
            throw new IllegalArgumentException("Um no nao pode ser pai de si mesmo.");
        if (parentId != null && all.stream().noneMatch(n -> parentId.equals(n.id())))
            throw new IllegalArgumentException("No pai nao encontrado.");
        if (parentId != null && createsCycle(all, nodeId, parentId))
            throw new IllegalArgumentException("Hierarquia invalida: ciclo detectado.");
        for (int i = 0; i < all.size(); i++) {
            HierarchyNode n = all.get(i);
            if (!nodeId.equals(n.id())) continue;
            HierarchyNode updated = new HierarchyNode(
                    n.id(),
                    request.tipo().trim().toUpperCase(),
                    request.nome().trim(),
                    request.descricao() == null ? "" : request.descricao().trim(),
                    parentId,
                    request.ordem() != null ? request.ordem() : n.ordem(),
                    sanitizeMembros(request.membros()),
                    sanitizeLoIds(request.loIds()),
                    n.criadoEm());
            all.set(i, updated);
            saveNodes(all);
            return updated;
        }
        throw new IllegalArgumentException("No de hierarquia nao encontrado.");
    }

    // Move uma pessoa de uma estrutura para outra de forma atômica (uma única gravação).
    public List<HierarchyNode> moveMember(MoveHierarchyMemberRequest request) throws IOException {
        if (request == null || request.fromNodeId() == null || request.toNodeId() == null || request.nomePessoa() == null)
            throw new IllegalArgumentException("Requisicao de movimentacao invalida.");
        if (request.fromNodeId().equals(request.toNodeId())) return loadNodes();

        List<HierarchyNode> all = new ArrayList<>(loadNodes());
        int fromIdx = indexOf(all, request.fromNodeId());
        int toIdx = indexOf(all, request.toNodeId());
        if (fromIdx < 0 || toIdx < 0) throw new IllegalArgumentException("Estrutura de origem ou destino nao encontrada.");

        String alvo = norm(request.nomePessoa());
        HierarchyNode from = all.get(fromIdx);
        List<HierarchyMember> fromMembros = new ArrayList<>(from.membros() == null ? List.of() : from.membros());
        HierarchyMember movido = null;
        for (int i = 0; i < fromMembros.size(); i++) {
            if (norm(fromMembros.get(i).nomePessoa()).equals(alvo)) { movido = fromMembros.remove(i); break; }
        }
        if (movido == null) throw new IllegalArgumentException("Pessoa nao encontrada na estrutura de origem.");

        HierarchyNode to = all.get(toIdx);
        List<HierarchyMember> toMembros = new ArrayList<>(to.membros() == null ? List.of() : to.membros());
        boolean jaExiste = toMembros.stream().anyMatch(m -> norm(m.nomePessoa()).equals(alvo));
        if (!jaExiste) toMembros.add(movido);

        all.set(fromIdx, withMembros(from, fromMembros));
        all.set(toIdx, withMembros(to, toMembros));
        saveNodes(all);
        return all;
    }

    private int indexOf(List<HierarchyNode> all, String id) {
        for (int i = 0; i < all.size(); i++) if (all.get(i) != null && id.equals(all.get(i).id())) return i;
        return -1;
    }

    private HierarchyNode withMembros(HierarchyNode n, List<HierarchyMember> membros) {
        return new HierarchyNode(n.id(), n.tipo(), n.nome(), n.descricao(), n.parentId(), n.ordem(),
                membros, n.loIds(), n.criadoEm());
    }

    private String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public void deleteNode(String nodeId) throws IOException {
        List<HierarchyNode> all = new ArrayList<>(loadNodes());
        // Remove o no e, em cascata, todos os descendentes.
        Set<String> toRemove = collectSubtree(all, nodeId);
        boolean removed = all.removeIf(n -> n != null && toRemove.contains(n.id()));
        if (!removed) throw new IllegalArgumentException("No de hierarquia nao encontrado.");
        saveNodes(all);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void validate(CreateHierarchyNodeRequest request) {
        if (request == null) throw new IllegalArgumentException("Requisicao invalida.");
        if (request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome do no e obrigatorio.");
        if (request.tipo() == null || !TIPOS_VALIDOS.contains(request.tipo().trim().toUpperCase()))
            throw new IllegalArgumentException("Tipo deve ser PRESIDENCIA, DIRETORIA, TRIBO ou SQUAD.");
    }

    private String normalizeParent(String parentId) {
        return parentId == null || parentId.isBlank() ? null : parentId.trim();
    }

    private List<HierarchyMember> sanitizeMembros(List<HierarchyMember> membros) {
        if (membros == null) return List.of();
        List<HierarchyMember> out = new ArrayList<>();
        for (HierarchyMember m : membros) {
            if (m == null || m.nomePessoa() == null || m.nomePessoa().isBlank()) continue;
            String vinculo = m.vinculo() == null ? null : m.vinculo().trim().toUpperCase();
            if (vinculo != null && !vinculo.equals("FOLHA") && !vinculo.equals("TERCEIRO")) vinculo = null;
            Double percentual = m.percentual();
            if (percentual != null) percentual = Math.max(0d, Math.min(100d, percentual));
            out.add(new HierarchyMember(
                    m.personId() == null ? null : m.personId().trim(),
                    m.nomePessoa().trim(),
                    m.papel() == null ? "" : m.papel().trim(),
                    Boolean.TRUE.equals(m.cross()),
                    vinculo,
                    percentual,
                    m.subgrupo() == null ? null : m.subgrupo().trim()));
        }
        return out;
    }

    private List<String> sanitizeLoIds(List<String> loIds) {
        if (loIds == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String id : loIds) {
            if (id == null || id.isBlank()) continue;
            String trimmed = id.trim();
            if (!out.contains(trimmed)) out.add(trimmed);
        }
        return out;
    }

    private int nextOrdem(List<HierarchyNode> all, String parentId) {
        return (int) all.stream()
                .filter(n -> java.util.Objects.equals(n.parentId(), parentId))
                .count();
    }

    private boolean createsCycle(List<HierarchyNode> all, String nodeId, String parentId) {
        String cursor = parentId;
        int guard = 0;
        while (cursor != null && guard++ < all.size() + 1) {
            if (cursor.equals(nodeId)) return true;
            cursor = parentIdOf(all, cursor);
        }
        return false;
    }

    private String parentIdOf(List<HierarchyNode> all, String nodeId) {
        for (HierarchyNode node : all) {
            if (node != null && node.id() != null && node.id().equals(nodeId)) {
                return node.parentId();
            }
        }
        return null;
    }

    private Set<String> collectSubtree(List<HierarchyNode> all, String rootId) {
        Set<String> result = new java.util.HashSet<>();
        result.add(rootId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (HierarchyNode n : all) {
                if (n.parentId() != null && result.contains(n.parentId()) && !result.contains(n.id())) {
                    result.add(n.id());
                    changed = true;
                }
            }
        }
        return result;
    }

    private List<HierarchyNode> loadNodes() throws IOException {
        return jsonStore.readList(hierarchyPath, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void saveNodes(List<HierarchyNode> nodes) throws IOException {
        jsonStore.writeList(hierarchyPath, nodes);
    }
}
