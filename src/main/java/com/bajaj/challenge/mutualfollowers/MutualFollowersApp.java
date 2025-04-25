package com.bajaj.challenge.mutualfollowers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

@SpringBootApplication
public class MutualFollowersApp implements CommandLineRunner {

    private static final String INIT_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
    private static final String NAME = "SLV HEMANTH KUMAR";
    private static final String REG_NO = "RA2211027010226";
    private static final String EMAIL = "hs1768@srmist.edu.in";

    public static void main(String[] args) {
        SpringApplication.run(MutualFollowersApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        RestTemplate restTemplate = new RestTemplate();

        Map<String, String> requestPayload = new HashMap<>();
        requestPayload.put("name", NAME);
        requestPayload.put("regNo", REG_NO);
        requestPayload.put("email", EMAIL);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestPayload, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(INIT_URL, requestEntity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> body = response.getBody();

            System.out.println("🔍 Full API Response:");
            System.out.println(body);

            String webhook = (String) body.get("webhook");
            String accessToken = (String) body.get("accessToken");

            Object dataObj = body.get("data");
            if (dataObj instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) dataObj;
                Object usersObj = data.get("users");

                if (usersObj instanceof List) {
                    List<Map<String, Object>> users = (List<Map<String, Object>>) usersObj;

                    List<List<Integer>> outcome = findMutualFollowers(users);

                    Map<String, Object> finalAnswer = new HashMap<>();
                    finalAnswer.put("regNo", REG_NO);
                    finalAnswer.put("outcome", outcome);

                    System.out.println("📤 Final Answer Sent:");
                    System.out.println(finalAnswer);

                    sendWithRetry(webhook, accessToken, finalAnswer, 4);
                } else {
                    System.out.println("❌ 'users' is not a List.");
                }
            } else {
                System.out.println("❌ 'data' is not a Map.");
            }
        } else {
            System.out.println("❌ Failed to get response from initial POST.");
        }
    }

    private List<List<Integer>> findMutualFollowers(List<Map<String, Object>> users) {
        Map<Integer, Set<Integer>> followsMap = new HashMap<>();
        for (Map<String, Object> user : users) {
            int id = (int) user.get("id");
            List<Integer> follows = (List<Integer>) user.get("follows");
            followsMap.put(id, new HashSet<>(follows));
        }

        Set<String> visitedPairs = new HashSet<>();
        List<List<Integer>> result = new ArrayList<>();

        for (Map.Entry<Integer, Set<Integer>> entry : followsMap.entrySet()) {
            int a = entry.getKey();
            for (int b : entry.getValue()) {
                if (followsMap.containsKey(b) && followsMap.get(b).contains(a)) {
                    int min = Math.min(a, b);
                    int max = Math.max(a, b);
                    String key = min + "," + max;
                    if (!visitedPairs.contains(key)) {
                        visitedPairs.add(key);
                        result.add(Arrays.asList(min, max));
                    }
                }
            }
        }
        return result;
    }

    private void sendWithRetry(String webhook, String token, Map<String, Object> body, int maxRetries) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(webhook, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    System.out.println("✅ Successfully posted result.");
                    break;
                } else {
                    attempts++;
                    System.out.println("❌ Failed attempt " + attempts);
                }
            } catch (Exception e) {
                attempts++;
                System.out.println("🔁 Retrying due to error: " + e.getMessage());
            }
        }
    }
}
