package com.clinica.chatbot.controller;

import com.clinica.chatbot.service.ChatBrain;
import com.clinica.chatbot.service.TwilioSender;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/twilio")
public class TwilioWebhookController {

    private final ChatBrain brain;
    private final TwilioSender sender;

    public TwilioWebhookController(ChatBrain brain, TwilioSender sender) {
        this.brain = brain;
        this.sender = sender;
    }

    // Configure na Twilio em: WHEN A MESSAGE COMES IN -> https://SEU_NGROK/twilio/whatsapp
    @PostMapping("/whatsapp")
    public ResponseEntity<String> inbound(HttpServletRequest req) throws IOException {
        String from = req.getParameter("From");   // e.g. whatsapp:+55859...
        String body = req.getParameter("Body");   // texto enviado

        // usa o número do remetente como sessionId
        String sessionId = (from == null) ? "anon" : from.replace("whatsapp:", "");
        String reply = brain.handle(sessionId, body == null ? "" : body);

        // responde via API Twilio (retorne 200 OK para evitar retries)
        sender.sendText(from, reply).subscribe();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("OK");
    }
}
