/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.jobs.source.occupation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * The deterministic half of occupation classification (trabalharei <strong>TRA22</strong>): an
 * ordered list of title patterns, each naming one CBO 2002 family code. Pure and static.
 *
 * <p>
 * <strong>Normalization</strong> ({@link #normalize(String)}): HTML stripped, lower case, accents
 * stripped, the gender marks {@code (a)}, {@code (o)}, {@code /a} removed ("Técnico(a)"), every run of
 * non-alphanumerics one space, and the seniority words dropped: {@code jr}, {@code junior},
 * {@code pleno}, {@code pl}, {@code sr}, {@code senior}, {@code estagio}, {@code estagiario(a)},
 * {@code trainee}, {@code aprendiz} and {@code jovem aprendiz}. So "Desenvolvedor Java Pleno" and
 * "Desenvolvedor Java Sênior" match alike, and "Jovem Aprendiz Auxiliar Administrativo" is read as
 * "auxiliar administrativo". An internship or apprenticeship that names no role ("Estágio em
 * Administração", "Jovem Aprendiz") matches nothing and stays unclassified.
 *
 * <p>
 * <strong>Matching</strong>: every rule's pattern is searched in the normalized title between word
 * boundaries, and the match that starts earliest wins, because a title names the role first and the
 * employer or the shift after it ("Recepcionista Clínica Veterinária", "Engenheiro Eletricista",
 * "Ajudante de Motorista"). Two matches at the same place go to the rule listed first, so specific rules
 * come before generic ones ("Vendedor Externo" before "Vendedor", "Mecânico de Manutenção" before
 * "Mecânico", "Programador CNC" before "Programador"). In a pattern a {@code " _ "} stands for an
 * optional preposition ({@code de}, {@code da}, {@code do}, {@code em}, ...), so "auxiliar _ limpeza"
 * matches both "Auxiliar de Limpeza" and "Auxiliar Limpeza".
 *
 * <p>
 * <strong>Ambiguous rules</strong> name no code: a title that matches one first is left for the
 * fallback classifier rather than taken by a later, wrong rule ("Professor" alone names no level,
 * "Desenvolvedor de Negócios" is a sales role, "Arquiteto de Software" is not an architect).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobOccupationDictionary {

    /** One pattern and the family it names; a {@code null} code is an ambiguous title. */
    public record Rule(String code, Pattern pattern) {
    }

    private static final Pattern GENDER = Pattern.compile("\\s?\\((?:a|o|as|os|es)\\)|/(?:a|o|as|os)\\b");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern JOVEM_APRENDIZ = Pattern.compile("\\bjovem aprendiz\\b");

    /** Seniority and programme words, ignored for matching. */
    private static final Set<String> IGNORED = Set.of("jr", "junior", "pleno", "plena", "pl", "sr",
            "senior", "estagio", "estagiario", "estagiaria", "estagiarios", "trainee", "aprendiz");

    private static final String PREPOSITION = "(?:(?:de|da|do|das|dos|em|na|no|nas|nos|para) )?";

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // --- ambiguous first: a later generic rule would read them wrong -----------------------
        ambiguous("desenvolvedor(?:a|es|as)? _ negocios");
        ambiguous("arquitet[oa]s? _ (?:software|solucoes|sistemas|dados|cloud|nuvem)");
        ambiguous("tecnic[oa]s? _ (?:mecanica|mecanico)");
        ambiguous("auxiliar(?:es)? veterinari[oa]s?|auxiliar _ veterinaria");

        // --- management --------------------------------------------------------------------------
        rule("1425", "gerentes? _ (?:ti|tecnologia(?: _ informacao)?)");
        rule("1422", "gerentes? _ (?:rh|recursos humanos)");
        rule("1423", "gerentes? _ (?:vendas|marketing|comercial|contas)|gerente comercial");
        rule("1414", "gerentes? _ (?:loja|supermercado|filial)");
        rule("1416", "gerentes? _ logistica");
        rule("1421", "gerentes? (?:administrativ[oa]|financeir[oa])");
        rule("5201", "supervisor(?:a|es)? _ vendas");
        rule("4101", "supervisor(?:a|es)? administrativ[oa]s?");
        rule("7102", "mestres? _ obras?|encarregad[oa]s? _ obras?");

        // --- engineering and other degree professions -------------------------------------------
        rule("2122", "engenheir[oa]s? _ (?:software|dados|computacao|machine learning)"
                + "|software engineer|data engineer");
        rule("2142", "engenheir[oa]s? civis|engenheir[oa] civil");
        rule("2143", "engenheir[oa]s? (?:eletricistas?|eletric[oa]s?|eletronic[oa]s?)");
        rule("2144", "engenheir[oa]s? mecanic[oa]s?");
        rule("2145", "engenheir[oa]s? quimic[oa]s?");
        rule("2140", "engenheir[oa]s? ambienta(?:l|is)");
        rule("2149", "engenheir[oa]s? _ (?:producao|qualidade|seguranca _ trabalho)");
        rule("2221", "engenheir[oa]s? agronom[oa]s?|agronom[oa]s?");
        rule("2141", "arquitet[oa]s?(?: urbanistas?)?");
        rule("2410", "advogad[oa]s?");
        rule("2522", "contador(?:a|es|as)?|analistas? _ (?:contabil|contabilidade|fiscal|tributari[oa]|custos)");
        rule("2524", "analistas? _ (?:rh|recursos humanos|departamento pessoal|dp|treinamento"
                + "|recrutamento(?: e selecao)?|selecao|remuneracao)|recrutador(?:a|es|as)?|tech recruiter");
        rule("2525", "analistas? _ (?:financeir[oa]|financas|credito|tesouraria)");
        rule("2527", "analistas? _ logistica");
        rule("3911", "(?:analistas?|tecnic[oa]s?|assistentes?|auxiliar(?:es)?) _ "
                + "(?:pcp|planejamento _ (?:controle _ )?producao)");
        rule("3542", "compradora?|compradores|analistas? _ compras");
        rule("3543", "analistas? _ comercio exterior|comex");
        rule("2531", "(?:analistas?|assistentes?|auxiliar(?:es)?|especialistas?) _ (?:marketing|midias sociais)"
                + "|marketing digital|social media|publicitari[oa]s?|gestor(?:a)? _ trafego(?: pago)?");
        rule("2523", "secretari[oa]s? executiv[oa]s?");
        rule("2515", "psicolog[oa]s?");
        rule("2516", "assistentes? socia(?:l|is)");
        rule("2611", "jornalistas?|reporter(?:es)?");
        rule("2615", "redator(?:a|es|as)?|copywriter");
        rule("2618", "fotografo|fotografa|fotografos");

        // --- health ------------------------------------------------------------------------------
        rule("3222", "(?:tecnic[oa]s?|auxiliar(?:es)?) _ enfermagem");
        rule("2235", "enfermeir[oa]s?");
        rule("2251", "medic[oa]s? (?:clinic[oa]s?(?: geral)?|generalistas?)|clinic[oa] geral");
        rule("3224", "(?:auxiliar(?:es)?|tecnic[oa]s?) _ (?:saude bucal|consultorio dentario|odontologia|dentista)");
        rule("2232", "cirurgi(?:ao|a|oes)[ ]dentistas?|dentistas?|odontolog[oa]s?");
        rule("2236", "fisioterapeutas?");
        rule("2237", "nutricionistas?");
        rule("2238", "fonoaudiolog[oa]s?");
        rule("2234", "farmaceutic[oa]s?");
        rule("2212", "biomedic[oa]s?");
        rule("2233", "(?:medic[oa]s? )?veterinari[oa]s?");
        rule("3241", "tecnic[oa]s? _ radiologia|tecnologo _ radiologia");
        rule("3242", "tecnic[oa]s? _ (?:analises clinicas|patologia clinica)");
        rule("5151", "agentes? comunitari[oa]s? _ saude");
        rule("5162", "cuidador(?:a|es|as)?|babas?");
        rule("3221", "esteticistas?|massoterapeutas?");
        rule("5161", "cabeleireir[oa]s?|manicures?|pedicures?|barbeir[oa]s?|maquiador(?:a|es|as)?"
                + "|designers? _ sobrancelhas?");
        rule("2629", "designers? _ interiores");
        rule("2624", "designers?(?: grafic[oa]s?)?|web designers?|desenhista industrial|ilustrador(?:a|es|as)?");
        rule("5193", "banhistas? _ (?:pets?|animais|caes|pet shop|petshop)|tosador(?:a|es|as)?|banho e tosa");

        // --- education ---------------------------------------------------------------------------
        rule("2311", "professor(?:a|es|as)? _ educacao infantil");
        rule("2321", "professor(?:a|es|as)? _ ensino medio");
        rule("2241", "professor(?:a|es|as)? _ (?:educacao fisica|natacao|musculacao)|personal trainer"
                + "|educador(?:a)? fisic[oa]|instrutor(?:a|es|as)? _ (?:pilates|musculacao|academia)");
        rule("2392", "professor(?:a|es|as)? _ educacao especial");
        rule("3331", "professor(?:a|es|as)? _ (?:ingles|espanhol|frances|alemao|italiano|idiomas?|informatica"
                + "|musica|violao|piano|canto|danca|curso livre)|instrutor(?:a|es|as)? _ "
                + "(?:idiomas|informatica|cursos?|autoescola|auto escola)");
        rule("3341", "inspetor(?:a|es|as)? _ alunos");
        rule("2394", "coordenador(?:a)? pedagogic[oa]|orientador(?:a)? educacional");
        ambiguous("professor(?:a|es|as)?|docentes?");

        // --- information technology --------------------------------------------------------------
        rule("2123", "administrador(?:a|es)? _ (?:redes|banco _ dados|sistemas)|analistas? _ (?:infraestrutura|redes)"
                + "|dba|devops|sysadmin");
        rule("2124", "analistas? _ (?:sistemas|ti|tecnologia _ informacao|suporte(?: tecnico)?|dados|bi"
                + "|business intelligence|testes|qa|seguranca _ informacao|desenvolvimento(?: _ sistemas)?)"
                + "|cientistas? _ dados|data (?:analyst|scientist)|desenvolvedor(?:a|es|as)?|developer|dev"
                + "|front ?end|back ?end|full ?stack|testers?");
        rule("7214", "operador(?:a|es)? _ (?:cnc|torno cnc|centro _ usinagem)|programador(?:a)? cnc");
        rule("3171", "programador(?:a|es|as)?|programmer");
        rule("3172", "(?:tecnic[oa]s?|auxiliar(?:es)?|assistentes?|atendentes?) _ (?:suporte(?: tecnico)?|helpdesk"
                + "|help desk|service desk|informatica)|suporte tecnico|help ?desk|service desk");

        // --- technicians -------------------------------------------------------------------------
        rule("3516", "tecnic[oa]s? _ seguranca _ trabalho");
        rule("3131", "tecnic[oa]s? _ (?:eletrotecnica|eletricidade)|eletrotecnic[oa]s?");
        rule("3132", "tecnic[oa]s? _ eletronica");
        rule("3121", "tecnic[oa]s? _ edificacoes");
        rule("3511", "tecnic[oa]s? _ contabilidade");
        rule("3912", "(?:inspetor(?:a|es)?|tecnic[oa]s?|auxiliar(?:es)?) _ (?:controle _ )?qualidade");
        rule("9112", "(?:mecanic[oa]s?|tecnic[oa]s?|instalador(?:a|es)?) _ (?:refrigeracao|ar condicionado)"
                + "|refrigeristas?");

        // --- administration and office -----------------------------------------------------------
        rule("4131", "(?:auxiliar(?:es)?|assistentes?) (?:contab(?:il|eis)|fiscal|_ contabilidade)");
        rule("4110", "(?:auxiliar(?:es)?|assistentes?|agentes?) administrativ[oa]s?"
                + "|(?:auxiliar(?:es)?|assistentes?) _ (?:escritorio|departamento pessoal|dp|rh|recursos humanos|pessoal)"
                + "|escriturari[oa]s?");
        rule("2521", "administrador(?:a|es|as)?");
        rule("5174", "porteir[oa]s?|vigias?|controlador(?:a|es)? _ acesso|(?:agentes?|auxiliar(?:es)?) _ portaria");
        rule("4221", "recepcionistas?|(?:auxiliar(?:es)?|atendentes?) _ recepcao");
        rule("3515", "secretari[oa]s?");
        rule("4223", "(?:operador(?:a|es|as)?|atendentes?|agentes?) _ (?:telemarketing|televendas|call center"
                + "|central _ atendimento|sac)|telemarketing|televendas|teleatendimento");
        rule("5112", "cobrador(?:a|es)? _ (?:onibus|transporte coletivo)");
        rule("4213", "(?:operador(?:a|es|as)?|agentes?|assistentes?|auxiliar(?:es)?) _ cobranca|cobrador(?:a|es|as)?");
        rule("4122", "office (?:boy|girl)|mensageir[oa]s?|continuo");
        rule("4152", "carteir[oa]s?");
        rule("3422", "despachantes? aduaneir[oa]s?");

        // --- commerce ----------------------------------------------------------------------------
        rule("4211", "operador(?:a|es|as)? _ caixa|(?:auxiliar(?:es)?|atendentes?) _ caixa|^caixas?$"
                + "|bilheteir[oa]s?");
        rule("3546", "corretor(?:a|es|as)? _ imoveis|corretor(?:a)? imobiliari[oa]");
        rule("3545", "corretor(?:a|es|as)? _ seguros");
        rule("3547", "representantes? comercia(?:l|is)|representantes? _ vendas");
        rule("3541", "vendedor(?:a|es|as)? (?:extern[oa]s?|pracistas?)");
        rule("5211", "vendedor(?:a|es|as)?|balconistas?|frentistas?|repositor(?:a|es|as)?"
                + "|promotor(?:a|es|as)? _ (?:vendas|merchandising)|atendentes? _ (?:loja|balcao|farmacia"
                + "|drogaria|varejo|supermercado|mercado)|operador(?:a|es)? _ loja");

        // --- logistics ---------------------------------------------------------------------------
        rule("4141", "estoquistas?|almoxarifes?|armazenistas?|(?:auxiliar(?:es)?|assistentes?|ajudantes?) _ "
                + "(?:almoxarifado|estoque|logistica|armazem)|controlador(?:a)? _ estoque");
        rule("4142", "conferentes?|apontador(?:a|es)?");
        rule("7822", "(?:operador(?:a|es)?) _ (?:empilhadeira|paleteira|transpaleteira)|empilhadeiristas?");
        rule("7821", "operador(?:a|es)? _ (?:ponte rolante|guindaste|munck)");
        rule("7832", "(?:ajudantes?|auxiliar(?:es)?) _ (?:carga e descarga|carga|motoristas?)|carregador(?:a|es)?|estivador(?:es)?");
        rule("7841", "embalador(?:a|es|as)?|(?:auxiliar(?:es)?|operador(?:a|es)?) _ embalagem");

        // --- drivers -----------------------------------------------------------------------------
        rule("7824", "motoristas? _ (?:onibus|micro onibus|microonibus|transporte coletivo)");
        rule("7823", "motoristas? _ (?:aplicativo|app|carro|executivo|particular|van|utilitario|furgao"
                + "|ambulancia)|motoristas? (?:cnh|categoria) b");
        rule("7825", "motoristas? _ (?:caminhao|carreta|truck|bitrem|cargas?|entregas?)|carreteir[oa]s?"
                + "|caminhoneir[oa]s?|motoristas?");
        rule("5191", "motoboys?|motofretistas?|entregador(?:a|es|as)?");
        rule("6410", "tratoristas?|operador(?:a|es)? _ (?:colheitadeira|maquinas agricolas)");
        rule("7151", "operador(?:a|es)? _ (?:retroescavadeira|retro escavadeira|escavadeira|pa carregadeira"
                + "|motoniveladora|rolo compactador|maquinas pesadas)");

        // --- construction and maintenance trades -------------------------------------------------
        rule("7321", "eletricistas? _ (?:rede|redes _ distribuicao|linha viva|linhas _ transmissao)");
        rule("9531", "eletricistas? (?:automotiv[oa]s?|_ autos?|_ veiculos)|auto ?eletric[oa]s?");
        rule("9511", "eletricistas? _ manutencao|eletricistas? industria(?:l|is)");
        rule("7156", "eletricistas?");
        rule("7152", "pedreir[oa]s?");
        rule("7170", "(?:serventes?|ajudantes?) _ (?:obras?|pedreir[oa]|construcao civil)");
        rule("7153", "armador(?:a|es)?(?: _ ferragens?)?");
        rule("7155", "carpinteir[oa]s?");
        rule("7164", "gesseir[oa]s?|drywall");
        rule("7165", "azulejistas?|assentador(?:a|es)? _ (?:pisos?|revestimentos?)");
        rule("7233", "pintor(?:a|es)? _ (?:automotiv[oa]|automoveis|veiculos|autos?|carros|industrial)");
        rule("7166", "pintor(?:a|es|as)?");
        rule("7241", "encanador(?:a|es)?|bombeir[oa]s? hidraulic[oa]s?|instalador(?:a|es)? hidraulic[oa]s?");
        rule("5171", "bombeir[oa]s? civis|bombeir[oa] civil|brigadistas?|salva vidas");
        rule("7243", "soldador(?:a|es|as)?");
        rule("7244", "serralheir[oa]s?|caldeireir[oa]s?");
        rule("7212", "torneir[oa]s?(?: mecanic[oa]s?)?|fresador(?:a|es)?");
        rule("7711", "marceneir[oa]s?");
        rule("7741", "montador(?:a|es)? _ moveis");
        rule("7632", "costureir[oa]s?|operador(?:a|es|as)? _ maquinas? _ costura");
        rule("9113", "mecanic[oa]s? _ manutencao(?: industrial)?|mecanic[oa]s? industria(?:l|is)");
        rule("9144", "mecanic[oa]s?");
        rule("5143", "(?:auxiliar(?:es)?|ajudantes?|agentes?|operador(?:a|es)?|serventes?) _ "
                + "(?:limpeza|servicos gerais|conservacao|manutencao predial)|faxineir[oa]s?|asg"
                + "|manutencao predial");
        rule("5141", "zelador(?:a|es)?");
        rule("5142", "garis?|coletor(?:a|es)? _ (?:lixo|residuos)|varredor(?:a|es)?");
        rule("5121", "empregad[oa]s? domestic[oa]s?|domesticas?|diaristas?|caseir[oa]s?");
        rule("5133", "camareir[oa]s?|arrumadeir[oa]s?");
        rule("6220", "jardineir[oa]s?");
        rule("5199", "lavador(?:a|es)? _ (?:veiculos|carros|autos)|lava jato");

        // --- security ----------------------------------------------------------------------------
        rule("5173", "vigilantes?|agentes? _ seguranca|seguranca patrimonial|^segurancas?$");

        // --- food and hospitality ----------------------------------------------------------------
        rule("2711", "chef(?:e)? _ cozinha|sous chef");
        rule("5136", "pizzaiol[oa]s?|sushim[ae]n|churrasqueir[oa]s?");
        rule("5135", "(?:auxiliar(?:es)?|ajudantes?) _ cozinha|lavador(?:a|es)? _ pratos|steward");
        rule("5132", "cozinheir[oa]s?");
        rule("8483", "padeir[oa]s?|confeiteir[oa]s?|(?:auxiliar(?:es)?|ajudantes?) _ (?:padaria|confeitaria)");
        rule("8485", "acougueir[oa]s?|desossador(?:a|es)?");
        rule("5134", "garcom|garcons|garconetes?|barman|bartenders?|baristas?|copeir[oa]s?|maitre"
                + "|atendentes? _ (?:lanchonete|restaurante|bar|cafeteria|fast food)");

        // --- production --------------------------------------------------------------------------
        rule("7842", "(?:auxiliar(?:es)?|ajudantes?) _ (?:producao|linha _ producao)|alimentador(?:a|es)? _ linha");
        rule("6210", "trabalhador(?:a|es)? rura(?:l|is)|lavrador(?:a|es)?");
        rule("3714", "recreador(?:a|es|as)?|monitor(?:a|es)? _ recreacao");
    }

    private DumJobOccupationDictionary() {
    }

    /** Every rule, in matching order. */
    public static List<Rule> rules() {
        return List.copyOf(RULES);
    }

    /** The family code the dictionary reads in {@code title}; empty when no rule, or an ambiguous one, matches. */
    public static Optional<String> classify(String title) {
        String normalized = normalize(title);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        Rule best = null;
        int bestStart = Integer.MAX_VALUE;
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(normalized);
            if (matcher.find() && matcher.start() < bestStart) {
                best = rule;
                bestStart = matcher.start();
            }
        }
        return best == null ? Optional.empty() : Optional.ofNullable(best.code());
    }

    /**
     * The title as the rules read it, and the key of the classification cache: plain text, folded,
     * gender marks removed, words separated by single spaces, seniority words dropped.
     */
    public static String normalize(String title) {
        String plain = DumJobNormalizer.plainText(title);
        if (plain == null) {
            return "";
        }
        String folded = GENDER.matcher(DumJobNormalizer.fold(plain)).replaceAll("");
        String spaced = NON_ALNUM.matcher(folded).replaceAll(" ").trim();
        spaced = JOVEM_APRENDIZ.matcher(spaced).replaceAll(" ");
        StringBuilder out = new StringBuilder();
        for (String word : spaced.split(" ")) {
            if (word.isEmpty() || IGNORED.contains(word)) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word);
        }
        return out.toString();
    }

    private static void rule(String code, String pattern) {
        RULES.add(new Rule(code, compile(pattern)));
    }

    private static void ambiguous(String pattern) {
        RULES.add(new Rule(null, compile(pattern)));
    }

    /** Word boundaries around the whole alternation; {@code " _ "} is an optional preposition. */
    private static Pattern compile(String pattern) {
        String expanded = pattern.replace(" _ ", " " + PREPOSITION);
        return Pattern.compile("(?<![a-z0-9])(?:" + expanded + ")(?![a-z0-9])");
    }
}
