package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FirebaseAuthService {
    OkHttpClient client = new OkHttpClient();

    String json = Files.readString(Path.of(".env/firebaseConfig.json"));
    String key;

    public FirebaseAuthService() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(".env/firebaseConfig.json"));
        key = root.get("apiKey").asText();
    }

    public String signIn(String email, String password) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();

        obj.put("email", email);
        obj.put("password", password);
        obj.put("returnSecureToken", true);

        Request request = new Request.Builder()
                .url("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + key)
                .post(RequestBody.create(obj.toString(), MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()){
            String responseBody = response.body().string();
            JsonNode json = mapper.readTree(responseBody);

            if (!response.isSuccessful()){
                return null;
            }
            return json.get("idToken").asText();
        }
    }
}
