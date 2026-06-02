package com.github.models;

import java.util.LinkedHashMap;
import java.util.Map;

public record AddProjectItemRequest(String type, Long id, String owner, String repo, Integer number) {

    public static AddProjectItemRequest byId(String type, Long id) {
        return new AddProjectItemRequest(type, id, null, null, null);
    }

    public static AddProjectItemRequest byRepository(String type, String owner, String repo, Integer number) {
        return new AddProjectItemRequest(type, null, owner, repo, number);
    }

    public Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        if (id != null) {
            body.put("id", id);
        }
        if (owner != null) {
            body.put("owner", owner);
        }
        if (repo != null) {
            body.put("repo", repo);
        }
        if (number != null) {
            body.put("number", number);
        }
        return body;
    }
}
