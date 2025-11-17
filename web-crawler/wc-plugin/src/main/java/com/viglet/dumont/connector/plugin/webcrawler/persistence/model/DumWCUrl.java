package com.viglet.dumont.connector.plugin.webcrawler.persistence.model;

import com.viglet.dumont.spring.jpa.DumUuid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;

@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wc_url")
public class DumWCUrl implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @DumUuid
    @Column(name = "id", nullable = false)
    protected String id;
    protected String url;
}
