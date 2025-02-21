package heyblack.mineds.dsapi.response;

public interface ResponseHandler {
    void onContentChunk(String content, String reasoning_content);
    void onComplete(String message, boolean pullContentFromLastChat) throws Exception;
    void onError(String error);
}
