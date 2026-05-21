package com.example.demo.service;

import com.example.demo.dto.Ticket;
import com.example.demo.signature.DigitalSignatureService;
import org.springframework.stereotype.Service;

@Service
public class TicketSignatureService {
    private final DigitalSignatureService digitalSignatureService;

    public TicketSignatureService(DigitalSignatureService digitalSignatureService) {
        this.digitalSignatureService = digitalSignatureService;
    }

    public String sign(Ticket ticket) {
        return digitalSignatureService.sign(ticket);
    }
}
