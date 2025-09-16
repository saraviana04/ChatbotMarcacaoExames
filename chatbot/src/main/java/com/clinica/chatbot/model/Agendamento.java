package com.clinica.chatbot.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class Agendamento {
    private long id;
    private String nome;
    private String telefone;
    private String exame;
    private LocalDate data;
    private LocalTime hora;
    private String status; // AGENDADO/CANCELADO

    // getters/setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getExame() { return exame; }
    public void setExame(String exame) { this.exame = exame; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
