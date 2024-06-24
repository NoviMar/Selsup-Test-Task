package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final HttpClient client;
    private final ObjectMapper jsonMapper;
    private final Semaphore requestSemaphore;

    public CrptApi(TimeUnit rateLimitTimeUnit, int maxRequests) {
        this.client = HttpClient.newHttpClient();
        this.jsonMapper = new ObjectMapper();
        this.requestSemaphore = new Semaphore(maxRequests);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> requestSemaphore.release(maxRequests - requestSemaphore.availablePermits()),
                0, rateLimitTimeUnit.toSeconds(1), TimeUnit.SECONDS);
    }

    @Data
    public static class Root {
        private RootDescription docDescription;
        private String RootId;
        private String RootStatus;
        private String RootType;
        private boolean isImportRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<RootProduct> productList;
        private String registrationDate;
        private String registrationNumber;

        @Data
        public static class RootDescription {
            private String participantInn;
        }

        @Data
        public static class RootProduct {
            private String certificateRoot;
            private String certificateRootDate;
            private String certificateRootNumber;
            private String ownerInn;
            private String producerInn;
            private String productionDate;
            private String tnvedCode;
            private String uitCode;
            private String uituCode;
        }
    }

    public void submitRoot(String endpointUrl, Root rootData, String digitalSignature) {
        try {
            if (!requestSemaphore.tryAcquire()) {
                System.err.println("Превышен лимит запросов");
                return;
            }

            String jsonRequestBody = jsonMapper.writeValueAsString(rootData);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "application/json")
                    .header("Signature", digitalSignature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Документ успешно создан.");
            } else {
                System.err.println("Ошибка при создании документа. HTTP статус: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Ошибка при отправке запроса: " + e.getMessage());
        } finally {
            requestSemaphore.release();
        }
    }

    public static void main(String[] args) {
        CrptApi apiService = new CrptApi(TimeUnit.SECONDS, 5);
        CrptApi.Root root = new CrptApi.Root();
        String signature = "signature";
        apiService.submitRoot("https://ismp.crpt.ru/api/v3/lk/documents/create", root, signature);
    }
}
