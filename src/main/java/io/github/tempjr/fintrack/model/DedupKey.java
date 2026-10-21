package io.github.tempjr.fintrack.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Chave de deduplicação de uma transação dentro da conta (RF07, ADR-005).
 * <ul>
 *   <li>Com FITID: a chave é o próprio FITID.</li>
 *   <li>Sem FITID: "H:" + SHA-256 de data|valor|descrição normalizada (fallback defensivo).</li>
 * </ul>
 * A conta não entra na chave porque a unicidade no banco já é (conta_id, dedup_key).
 */
public final class DedupKey {

    private static final String PREFIXO_HASH = "H:";

    private DedupKey() {
    }

    public static String gerar(String fitId, LocalDate data, BigDecimal valor, String descricao) {
        if (fitId != null && !fitId.isBlank()) {
            return fitId.strip();
        }
        String conteudo = data + "|" + valor.setScale(2).toPlainString() + "|" + normalizar(descricao);
        return PREFIXO_HASH + sha256(conteudo);
    }

    /** "  Pix   enviado " e "PIX ENVIADO" geram a mesma chave. */
    static String normalizar(String descricao) {
        if (descricao == null) {
            return "";
        }
        return descricao.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM é obrigada a ter SHA-256; se faltar, é erro de ambiente.
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
