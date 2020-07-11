package com.example.abac_spike;

import lombok.Data;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.content.commons.annotations.MimeType;

import javax.persistence.*;

import static javax.persistence.GenerationType.AUTO;

@Entity
@Data
public class AccountState {

    @Id
    @GeneratedValue(strategy=AUTO)
    private Long id;

    private String brokerId = null;

    @ContentId
    private String contentId;

    @ContentLength
    private Long contentLength;

    @MimeType
    private String mimeType;

    private String name;
    private String type;

    @JoinColumn
    @ManyToOne
    private Broker bbroker;
}