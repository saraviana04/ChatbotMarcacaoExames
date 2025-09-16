package com.clinica.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class TwilioSender {

    @Value("${twilio.accountSid}") private String accountSid;
    @Value("${twilio.authToken}")  private String authToken;
    @Value("${twilio.from}")       private String from; // whatsapp:+14155238886 (sandbox)

    private WebClient client() {
        return WebClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01")
                .defaultHeaders(h -> h.setBasicAuth(accountSid, authToken))
                .build();
    }

    public Mono<String> sendText(String to, String body) {
        // Twilio espera to = "whatsapp:+55...."
        if (to == null || !to.startsWith("whatsapp:")) return Mono.just("skip");
        String form = "To=" + enc(to) + "&From=" + enc(from) + "&Body=" + enc(body);
        return client()
                .post()
                .uri("/Accounts/{sid}/Messages.json", accountSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(String.class);
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
