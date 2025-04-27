## Technology Stack: JAX-RS (Jersey) + Grizzly

This application utilizes **Jersey**, the reference implementation for the **JAX-RS** (Jakarta RESTful Web Services) standard, running on the embedded **Grizzly** HTTP server. While **Spring Boot** is a highly popular and powerful framework for building Java applications, the Jersey/Grizzly combination was chosen for this specific project due to:

* **Standard Compliance:** Jersey provides a direct implementation of the JAX-RS standard, ensuring adherence to established Java EE / Jakarta EE conventions for REST APIs.
* **Lightweight Focus:** For an application primarily focused on exposing a set of RESTful API endpoints wrapping another service, Jersey + Grizzly offers a more lightweight and focused solution with potentially less overhead compared to the broader Spring ecosystem.
* **Simplicity:** The combination allows for rapid development of REST endpoints using standard annotations without needing to incorporate the full breadth of features (like advanced dependency injection, data persistence layers, etc.) that Spring Boot readily provides, which might be overkill for this specific use case.
* **Modularity:** It allows adding specific functionalities as needed without inheriting a large opinionated framework structure by default.

While Spring Boot excels in building complex, feature-rich applications with its extensive ecosystem and auto-configuration capabilities, the focused, standards-based approach of Jersey/Grizzly was deemed a better fit for the defined scope of this API wrapper project.


## API Endpoints

### Text Generation (Simple)
- **Path:** `/generate/text`
- **Method:** `POST`
- **Input:** 
    ```json
    {
        "prompt": "..."
    }
    ```
    *(Content-Type: application/json)*
- **Output:** 
    ```json
    {
        "response": "..."
    }
    ```
    *(Content-Type: application/json)*
- **Java Method Signature:**
    ```java
    public Response generateText(TextInput input)
    ```
    *(Where `TextInput` is a POJO with a `prompt` field.)*

---

### Text Generation (Config)
- **Path:** `/generate/text/configured`
- **Method:** `POST`
- **Input:** 
    ```json
    {
        "prompt": "...",
        "config": {
            "temperature": 0.5,
            ...
        }
    }
    ```
    *(Content-Type: application/json)*
- **Output:** 
    ```json
    {
        "response": "..."
    }
    ```
    *(Content-Type: application/json)*
- **Java Method Signature:**
    ```java
    public Response generateTextConfigured(ConfiguredTextInput input)
    ```
    *(Where `ConfiguredTextInput` has `prompt` and `config` map/object.)*

---

### Image Generation
- **Path:** `/generate/image`
- **Method:** `POST`
- **Input:** 
    ```json
    {
        "prompt": "..."
    }
    ```
    *(Content-Type: application/json)*
- **Output:** Image bytes *(image/png or appropriate type)*
- **Java Method Signature:**
    ```java
    public Response generateImage(ImageInput input)
    ```
- **Return:**
    ```java
    Response.ok(imageBytes, "image/png").build();
    ```

---

### Image Understanding
- **Path:** `/understand/image`
- **Method:** `POST`
- **Input:** `multipart/form-data` with:
    - `prompt` field
    - `image` file part
- **Output:** 
    ```json
    {
        "description": "..."
    }
    ```
    *(Content-Type: application/json)*
- **Java Method Signature:**
    ```java
    public Response understandImage(
            @FormDataParam("prompt") String prompt,
            @FormDataParam("image") InputStream imageStream,
            @FormDataParam("image") FormDataContentDisposition fileDetail
    )
    ```
    *(Need to read bytes from `imageStream` and get MIME type, potentially from `fileDetail` or assume one.)*

---

### Document Understanding
- **Path:** `/understand/document`
- **Method:** `POST`
- **Input:** `multipart/form-data` with:
    - `prompt` field
    - `document` file part (PDF)
- **Output:** 
    ```json
    {
        "summary": "..."
    }
    ```
    *(Content-Type: application/json)*
- **Java Method Signature:**
    ```java
    public Response understandDocument(
            @FormDataParam("prompt") String prompt,
            @FormDataParam("document") InputStream documentStream,
            @FormDataParam("document") FormDataContentDisposition fileDetail
    )
    ```
    *(Need to handle potential `IOException` when reading/processing the document stream. May need to temporarily save the stream to a file if the `GeminiClient` requires a `Path`.)*

---

### Function Calling
- **Path:** `/execute/function-call`
- **Method:** `POST`
- **Input:** 
    ```json
    {
        "prompt": "...",
        "toolsJson": "[...]"
    }
    ```
    *(Content-Type: application/json)*
- **Output:** Raw JSON response from Gemini *(Content-Type: application/json)*
- **Java Method Signature:**
    ```java
    public Response executeFunctionCall(FunctionCallInput input)
    ```

---

### Code Execution
- **Path:** `/execute/code`
- **Method:** `POST`
- **Input:** 
    ```json
    {
        "prompt": "..."
    }
    ```
    *(Content-Type: application/json)*
- **Output:** Raw JSON response from Gemini *(Content-Type: application/json)*
- **Java Method Signature:**
    ```java
    public Response executeCode(CodeInput input)
    ```