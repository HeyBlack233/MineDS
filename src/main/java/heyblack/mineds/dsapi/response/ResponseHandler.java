package heyblack.mineds.dsapi.response;

import com.google.gson.JsonObject;

public interface ResponseHandler {
    void onContentChunk(String content, String reasoning_content);
    void onComplete(String message, boolean pullContentFromLastChat) throws Exception;
    void onError(JsonObject error);
}
