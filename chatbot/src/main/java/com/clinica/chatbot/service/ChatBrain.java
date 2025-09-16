package com.clinica.chatbot.service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ChatBrain {

    // === Modelo simples em memória ===
    public static class Agendamento {
        public long id;
        public String nome;
        public String telefone;
        public String exame; // Sangue, Urina, Raio-X, Tomografia
        public LocalDate date;
        public LocalTime time;
        public String status = "AGENDADO";

        public String resumo() {
            return "#" + id + " • " + exame + " • " +
                    date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) + " " + time;
        }
    }

    private enum Step { INICIO, PERG_NOME, PERG_FONE, PERG_EXAME, PERG_DATA, PERG_HORA, CONFIRMA }

    private static class State {
        Step step = Step.INICIO;
        String nome;
        String telefone;
        String exame;
        LocalDate data;
        LocalTime hora;
    }

    // Sessões por usuário (use o telefone/From do WhatsApp como sessionId)
    private final Map<String, State> sessions = new ConcurrentHashMap<>();
    // “Banco” em memória
    private final Map<Long, Agendamento> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    // Configuração de horários
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);

    public String handle(String sessionId, String raw) {
        String msg = raw == null ? "" : raw.trim();
        String low = msg.toLowerCase();
        State st = sessions.computeIfAbsent(sessionId, k -> new State());

        // Comandos rápidos
        if (low.matches(".*\\bcancelar\\b.*\\d+.*")) {
            long id = extractLong(low);
            Agendamento a = store.get(id);
            if (a == null || !"AGENDADO".equals(a.status)) return "Não encontrei o agendamento #" + id + ".";
            a.status = "CANCELADO";
            return "Agendamento #" + id + " cancelado com sucesso.";
        }
        if (low.contains("consultar") || low.contains("minhas") || low.contains("meus") || low.contains("ver")) {
            if (st.telefone == null) {
                st.step = Step.PERG_FONE;
                return "Informe seu telefone (apenas números):";
            }
            return listarPorTelefone(st.telefone);
        }

        // Fluxo padrão
        switch (st.step) {
            case INICIO -> {
                if (low.contains("marcar") || low.contains("agendar")) {
                    st.step = Step.PERG_NOME;
                    return "Perfeito! Qual é o seu nome completo?";
                }
                return "Olá! Sou o assistente de marcação de exames da clínica. Você quer **marcar**, **consultar** ou **cancelar #ID**?";
            }
            case PERG_NOME -> {
                if (msg.length() < 2) return "Pode repetir seu nome completo?";
                st.nome = msg;
                st.step = Step.PERG_FONE;
                return "Informe seu telefone (apenas números):";
            }
            case PERG_FONE -> {
                String digits = msg.replaceAll("[^0-9]", "");
                if (digits.length() < 8) return "Telefone inválido. Digite apenas números (DDD+telefone).";
                st.telefone = digits;
                st.step = Step.PERG_EXAME;
                return "Qual exame? (Sangue, Urina, Raio-X, Tomografia)";
            }
            case PERG_EXAME -> {
                String ex = normalizaExame(msg);
                if (ex == null) return "Não reconheci o exame. Responda: Sangue, Urina, Raio-X ou Tomografia.";
                st.exame = ex;
                st.step = Step.PERG_DATA;
                return "Para qual data? (formato DD/MM/AAAA) — Ex.: 22/10/2025";
            }
            case PERG_DATA -> {
                LocalDate d = parseDate(msg);
                if (d == null) return "Data inválida. Use DD/MM/AAAA (ex.: 22/10/2025).";
                st.data = d;
                List<LocalTime> slots = availableSlots(d);
                if (slots.isEmpty()) return "Não há horários disponíveis nessa data. Informe outra data (DD/MM/AAAA).";
                st.step = Step.PERG_HORA;
                return "Horários disponíveis:\n" + formatSlots(slots) + "Escolha um horário (HH:mm).";
            }
            case PERG_HORA -> {
                LocalTime t = parseTime(msg);
                if (t == null) return "Horário inválido. Use HH:mm (ex.: 09:30).";
                st.hora = t;
                st.step = Step.CONFIRMA;
                return "Confirmar agendamento?\n" +
                        "Paciente: " + st.nome + "\n" +
                        "Telefone: " + st.telefone + "\n" +
                        "Exame: " + st.exame + "\n" +
                        "Data/Hora: " + st.data.format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) + " " + st.hora +
                        "\nResponda SIM ou NÃO.";
            }
            case CONFIRMA -> {
                if (low.startsWith("s")) {
                    Agendamento a = new Agendamento();
                    a.id = seq.getAndIncrement();
                    a.nome = st.nome;
                    a.telefone = st.telefone;
                    a.exame = st.exame;
                    a.date = st.data;
                    a.time = st.hora;
                    store.put(a.id, a);
                    sessions.remove(sessionId);
                    return "Agendamento criado com sucesso!\n" +
                            "Código: #" + a.id + "\n" + a.resumo() + "\n" +
                            "Guarde este código para cancelar se precisar.";
                } else {
                    sessions.remove(sessionId);
                    return "Tudo bem, não confirmei. Posso ajudar com mais alguma coisa? (marcar / consultar)";
                }
            }
        }
        return "Não entendi. Você quer **marcar**, **consultar** ou **cancelar #ID**?";
    }

    private String listarPorTelefone(String fone) {
        StringBuilder sb = new StringBuilder("Seus agendamentos:\n");
        store.values().stream()
                .filter(a -> a.telefone.equals(fone) && "AGENDADO".equals(a.status))
                .sorted(Comparator.comparing(a -> LocalDateTime.of(a.date, a.time)))
                .forEach(a -> sb.append(a.resumo()).append("\n"));
        if (sb.toString().equals("Seus agendamentos:\n")) return "Você não possui agendamentos futuros.";
        sb.append("\nPara cancelar, envie: cancelar #ID");
        return sb.toString();
    }

    private List<LocalTime> availableSlots(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) return List.of();
        List<LocalTime> slots = new ArrayList<>();
        for (LocalTime t = OPEN; !t.plusMinutes(30).isAfter(CLOSE); t = t.plusMinutes(30)) {
            LocalDateTime start = LocalDateTime.of(date, t);
            if (start.isAfter(LocalDateTime.now())) slots.add(t);
        }
        return slots;
    }

    private static String formatSlots(List<LocalTime> s) {
        StringBuilder b = new StringBuilder();
        for (LocalTime t : s) b.append("• ").append(t).append("\n");
        return b.toString();
    }

    private static LocalDate parseDate(String s) {
        s = s.toLowerCase();
        if (s.contains("hoje")) return LocalDate.now();
        if (s.contains("amanh")) return LocalDate.now().plusDays(1);
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("d/M/uuuu")); }
        catch (Exception e) {
            try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/uuuu")); }
            catch (Exception ex) { return null; }
        }
    }

    private static LocalTime parseTime(String s) {
        try { return LocalTime.parse(s, DateTimeFormatter.ofPattern("H:mm")); }
        catch (Exception e) {
            try { return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm")); }
            catch (Exception ex) { return null; }
        }
    }

    private static long extractLong(String s) {
        var m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : -1L;
    }

    private static String normalizaExame(String s) {
        s = s == null ? "" : s.toLowerCase();
        if (s.contains("sang")) return "Sangue";
        if (s.contains("urina")) return "Urina";
        if (s.contains("raio")) return "Raio-X";
        if (s.contains("tomo")) return "Tomografia";
        return null;
    }
}
