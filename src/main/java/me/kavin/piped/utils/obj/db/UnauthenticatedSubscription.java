package me.kavin.piped.utils.obj.db;

import jakarta.persistence.*;

@Entity
@Table(name = "unauthenticated_subscriptions", indexes = {
        @Index(columnList = "id", name = "unauthenticated_subscriptions_id_idx"),
        @Index(columnList = "subscribed_at", name = "unauthenticated_subscriptions_subscribed_at_idx")
})
public class UnauthenticatedSubscription {

    public UnauthenticatedSubscription() {
    }

    public UnauthenticatedSubscription(String id) {
        this.id = id;
        this.subscribedAt = System.currentTimeMillis();
    }

    @Id
    @Column(name = "id", unique = true, nullable = false, length = 24)
    private String id;

    @Column(name = "subscribed_at", nullable = false)
    private long subscribedAt;

    public long getSubscribedAt() {
        return subscribedAt;
    }

    public void setSubscribedAt(long subscribedAt) {
        this.subscribedAt = subscribedAt;
    }

    public String getId() {
        return id;
    }
}
