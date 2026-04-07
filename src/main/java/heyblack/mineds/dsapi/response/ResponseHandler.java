package heyblack.mineds.dsapi.response;

import com.google.gson.JsonObject;
import heyblack.mineds.session.Session;

public interface ResponseHandler {
    void onContentChunk(String content, String reasoning_content);
    void onComplete(Session session, String message) throws Exception;
    void onError(JsonObject error);
}
