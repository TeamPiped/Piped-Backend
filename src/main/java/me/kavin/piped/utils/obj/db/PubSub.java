package me.kavin.piped.utils.obj.db;

import jakarta.persistence.*;

@Entity
@Table(name = "pubsub", indexes = {@Index(columnList = "id", name = "pubsub_id_idx"),
        @Index(columnList = "subbed_at", name = "pubsub_subbed_at_idx")})
public class PubSub {

    @Id
    @Column(name = "id", unique = true, length = 24)
    private String id;

    @Column(name = "subbed_at")
    private long subbedAt;

    public PubSub() {
    }

    public PubSub(String id, long subbedAt) {
        this.id = id;
        this.subbedAt = subbedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getSubbedAt() {
        return subbedAt;
    }

    public void setSubbedAt(long subbedAt) {
        this.subbedAt = subbedAt;
    }
}
