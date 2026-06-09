package com.planner.backend.project.hierarchy;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HierarchyService {

    private static final Set<String> TIPOS_VALIDOS = Set.of(
            "PRESIDENCIA", "VICE_PRESIDENCIA", "SUPERINTENDENCIA", "GERENCIA", "DIRETORIA", "TRIBO", "SQUAD");

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
        List<String> parents = normalizeParents(request);
        validarPaisExistem(all, parents, null);
        int ordem = request.ordem() != null ? request.ordem() : nextOrdem(all, parents);
        String tipo = normalizeTipo(request.tipo());
        HierarchyNode created = new HierarchyNode(
                UUID.randomUUID().toString(),
                tipo,
                request.nome().trim(),
                request.descricao() == null ? "" : request.descricao().trim(),
                parents.isEmpty() ? null : parents.get(0),
                parents,
                ordem,
                sanitizeMembros(request.membros(), tipo),
                sanitizeLoIds(request.loIds()),
                sanitizeProjetoIds(request.projetoIds()),
                sanitizeProjetoIds(request.projetosOcultos()),
                OffsetDateTime.now());
        all.add(created);
        saveNodes(all);
        return created;
    }

    public HierarchyNode updateNode(String nodeId, CreateHierarchyNodeRequest request) throws IOException {
        validate(request);
        List<HierarchyNode> all = new ArrayList<>(loadNodes());
        List<String> parents = normalizeParents(request);
        if (parents.contains(nodeId))
            throw new IllegalArgumentException("Um no nao pode ser pai de si mesmo.");
        validarPaisExistem(all, parents, nodeId);
        if (createsCycle(all, nodeId, parents))
            throw new IllegalArgumentException("Hierarquia invalida: ciclo detectado.");
        for (int i = 0; i < all.size(); i++) {
            HierarchyNode n = all.get(i);
            if (!nodeId.equals(n.id())) continue;
            String tipo = normalizeTipo(request.tipo());
            HierarchyNode updated = new HierarchyNode(
                    n.id(),
                    tipo,
                    request.nome().trim(),
                    request.descricao() == null ? "" : request.descricao().trim(),
                    parents.isEmpty() ? null : parents.get(0),
                    parents,
                    request.ordem() != null ? request.ordem() : n.ordem(),
                    sanitizeMembros(request.membros(), tipo),
                    sanitizeLoIds(request.loIds()),
                    sanitizeProjetoIds(request.projetoIds()),
                    sanitizeProjetoIds(request.projetosOcultos()),
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
        if (!jaExiste) toMembros.add(sanitizeMembro(movido, tipoPermiteMarcacoesDeTime(to.tipo())));

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
        return new HierarchyNode(n.id(), n.tipo(), n.nome(), n.descricao(), n.parentId(), parentsOf(n), n.ordem(),
                membros, n.loIds(), n.projetoIds(), n.projetosOcultos(), n.criadoEm());
    }

    private HierarchyNode withParents(HierarchyNode n, List<String> parents) {
        return new HierarchyNode(n.id(), n.tipo(), n.nome(), n.descricao(),
                parents.isEmpty() ? null : parents.get(0), parents, n.ordem(),
                n.membros(), n.loIds(), n.projetoIds(), n.projetosOcultos(), n.criadoEm());
    }

    private String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /**
     * Exclui a estrutura. Para cada filho, remove apenas o vinculo com a estrutura excluida;
     * o filho so e apagado se ficar sem nenhum pai (orfao), e nesse caso o efeito cascateia.
     */
    public void deleteNode(String nodeId) throws IOException {
        List<HierarchyNode> all = new ArrayList<>(loadNodes());
        if (all.stream().noneMatch(n -> n != null && nodeId.equals(n.id())))
            throw new IllegalArgumentException("No de hierarquia nao encontrado.");

        // Mapa mutavel id -> pais efetivos.
        Map<String, List<String>> parents = new LinkedHashMap<>();
        for (HierarchyNode n : all) parents.put(n.id(), new ArrayList<>(parentsOf(n)));

        Set<String> removed = new HashSet<>();
        Deque<String> fila = new ArrayDeque<>();
        fila.add(nodeId);
        while (!fila.isEmpty()) {
            String cur = fila.poll();
            if (!removed.add(cur)) continue;
            for (Map.Entry<String, List<String>> e : parents.entrySet()) {
                if (removed.contains(e.getKey())) continue;
                List<String> plist = e.getValue();
                if (plist.remove(cur) && plist.isEmpty()) {
                    fila.add(e.getKey()); // ficou orfao por causa da exclusao -> remove tambem
                }
            }
        }

        List<HierarchyNode> result = new ArrayList<>();
        for (HierarchyNode n : all) {
            if (removed.contains(n.id())) continue;
            result.add(withParents(n, parents.get(n.id())));
        }
        saveNodes(result);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void validate(CreateHierarchyNodeRequest request) {
        if (request == null) throw new IllegalArgumentException("Requisicao invalida.");
        if (request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome do no e obrigatorio.");
        if (request.tipo() == null || !TIPOS_VALIDOS.contains(request.tipo().trim().toUpperCase()))
            throw new IllegalArgumentException("Tipo deve ser PRESIDENCIA, VICE_PRESIDENCIA, SUPERINTENDENCIA, DIRETORIA, GERENCIA, TRIBO ou SQUAD.");
    }

    private String normalizeTipo(String tipo) {
        return tipo.trim().toUpperCase();
    }

    /** Pais efetivos de um no: parentIds quando presente; senao cai no parentId legado. */
    private List<String> parentsOf(HierarchyNode n) {
        List<String> out = new ArrayList<>();
        if (n.parentIds() != null) {
            for (String p : n.parentIds()) {
                if (p != null && !p.isBlank() && !out.contains(p.trim())) out.add(p.trim());
            }
        }
        if (out.isEmpty() && n.parentId() != null && !n.parentId().isBlank()) {
            out.add(n.parentId().trim());
        }
        return out;
    }

    /** Pais informados na requisicao (parentIds preferencial + parentId legado), deduplicados. */
    private List<String> normalizeParents(CreateHierarchyNodeRequest request) {
        List<String> out = new ArrayList<>();
        if (request.parentIds() != null) {
            for (String p : request.parentIds()) {
                if (p != null && !p.isBlank() && !out.contains(p.trim())) out.add(p.trim());
            }
        }
        if (request.parentId() != null && !request.parentId().isBlank() && !out.contains(request.parentId().trim())) {
            out.add(request.parentId().trim());
        }
        return out;
    }

    private void validarPaisExistem(List<HierarchyNode> all, List<String> parents, String ignoreId) {
        for (String p : parents) {
            boolean existe = all.stream().anyMatch(n -> p.equals(n.id()) && !p.equals(ignoreId));
            if (!existe) throw new IllegalArgumentException("No pai nao encontrado.");
        }
    }

    private boolean tipoPermiteMarcacoesDeTime(String tipo) {
        String normalized = tipo == null ? "" : tipo.trim().toUpperCase();
        return normalized.equals("TRIBO") || normalized.equals("SQUAD");
    }

    private List<HierarchyMember> sanitizeMembros(List<HierarchyMember> membros, String tipo) {
        if (membros == null) return List.of();
        List<HierarchyMember> out = new ArrayList<>();
        boolean permiteMarcacoes = tipoPermiteMarcacoesDeTime(tipo);
        for (HierarchyMember m : membros) {
            if (m == null || m.nomePessoa() == null || m.nomePessoa().isBlank()) continue;
            out.add(sanitizeMembro(m, permiteMarcacoes));
        }
        return out;
    }

    private HierarchyMember sanitizeMembro(HierarchyMember m, boolean permiteMarcacoes) {
        String vinculo = m.vinculo() == null ? null : m.vinculo().trim().toUpperCase();
        if (vinculo != null && !vinculo.equals("FOLHA") && !vinculo.equals("TERCEIRO")) vinculo = null;
        Double percentual = permiteMarcacoes ? m.percentual() : null;
        if (percentual != null) percentual = Math.max(0d, Math.min(100d, percentual));
        return new HierarchyMember(
                m.personId() == null ? null : m.personId().trim(),
                m.nomePessoa().trim(),
                m.papel() == null ? "" : m.papel().trim(),
                Boolean.TRUE.equals(m.cross()),
                vinculo,
                percentual,
                m.subgrupo() == null ? null : m.subgrupo().trim());
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

    private List<String> sanitizeProjetoIds(List<String> projetoIds) {
        if (projetoIds == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String id : projetoIds) {
            if (id == null || id.isBlank()) continue;
            String trimmed = id.trim();
            if (!out.contains(trimmed)) out.add(trimmed);
        }
        return out;
    }

    private int nextOrdem(List<HierarchyNode> all, List<String> parents) {
        String ref = parents.isEmpty() ? null : parents.get(0);
        return (int) all.stream()
                .filter(n -> ref == null ? parentsOf(n).isEmpty() : parentsOf(n).contains(ref))
                .count();
    }

    /** Em grafo (multi-pai), ha ciclo se algum pai novo for o proprio no ou um descendente dele. */
    private boolean createsCycle(List<HierarchyNode> all, String nodeId, List<String> newParents) {
        Set<String> descendentes = collectDescendants(all, nodeId);
        for (String p : newParents) {
            if (descendentes.contains(p)) return true;
        }
        return false;
    }

    /** Conjunto de descendentes de rootId (inclui rootId), seguindo os vinculos de pai para baixo. */
    private Set<String> collectDescendants(List<HierarchyNode> all, String rootId) {
        Set<String> result = new HashSet<>();
        result.add(rootId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (HierarchyNode n : all) {
                if (result.contains(n.id())) continue;
                for (String p : parentsOf(n)) {
                    if (result.contains(p)) { result.add(n.id()); changed = true; break; }
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
