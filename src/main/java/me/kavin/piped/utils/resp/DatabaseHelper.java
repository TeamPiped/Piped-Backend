package me.kavin.piped.utils.resp;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.Session;

import me.kavin.piped.utils.obj.db.User;

public class DatabaseHelper {

    public static final User getUserFromSession(Session s, String session) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<User> cr = cb.createQuery(User.class);
        Root<User> root = cr.from(User.class);
        cr.select(root).where(root.get("sessionId").in(session));

        return s.createQuery(cr).uniqueResult();
    }
}
