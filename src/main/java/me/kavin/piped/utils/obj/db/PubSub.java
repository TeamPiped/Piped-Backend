package me.kavin.piped.utils.obj.db;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

@Entity
@Table(name = "pubsub", indexes = { @Index(columnList = "id", name = "id_idx") })
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
