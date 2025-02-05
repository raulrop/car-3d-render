package perpro_3dengine;

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.lwjgl.BufferUtils;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

public class perpro_3dcar {

    // Window handle
    private long window;

    // Vertex Buffer Object (VBO) and Vertex Array Object (VAO) handles
    private int vaoId;
    private int vboId;

    // Shader program handle
    private int shaderProgram;

    // Model transformation matrix
    private FloatBuffer modelMatrix;

    // Camera look-at matrix
    private FloatBuffer viewMatrix;

    // Projection matrix
    private FloatBuffer projectionMatrix;

    // Model data
    private List<Float> vertices = new ArrayList<>();

    public void run() {
        init();
        loop();

        // Free resources
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        glDeleteProgram(shaderProgram);

        // Clean up GLFW
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }

    private void init() {
        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        // Create the window
        window = glfwCreateWindow(800, 600, "LWJGL OBJ Renderer", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Set up a key callback
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }
        });

        // Center the window
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth.get(0)) / 2,
                (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);
        // Make the window visible
        glfwShowWindow(window);

        // Initialize OpenGL
        GL.createCapabilities();

        // Load the model
        loadModel("toyota-gr-corolla.obj");

        // Set up shaders
        shaderProgram = createShaderProgram();

        // Set up matrices
        setupMatrices();

        // Set up VAO and VBO
        setupBuffers();
    }

    private void loadModel(String filePath) {
        AIScene scene = aiImportFile(filePath, aiProcess_Triangulate | aiProcess_FlipUVs);
        if (scene == null) {
            throw new RuntimeException("Failed to load model: " + aiGetErrorString());
        }

        // Process the model's meshes
        for (int i = 0; i < scene.mNumMeshes(); i++) {
            AIMesh mesh = AIMesh.create(scene.mMeshes().get(i));
            for (int j = 0; j < mesh.mNumVertices(); j++) {
                AIVector3D vertex = mesh.mVertices().get(j);
                vertices.add(vertex.x());
                vertices.add(vertex.y());
                vertices.add(vertex.z());
            }
        }
    }

    private int createShaderProgram() {
        String vertexShaderSource = "#version 330 core\n" +
            "layout(location = 0) in vec3 aPos;\n" +
            "uniform mat4 model;\n" +
            "uniform mat4 view;\n" +
            "uniform mat4 projection;\n" +
            "void main() {\n" +
            "   gl_Position = projection * view * model * vec4(aPos, 1.0);\n" +
            "}\n";

        String fragmentShaderSource = "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "void main() {\n" +
            "   FragColor = vec4(1.0, 1.0, 1.0, 1.0);\n" +
            "}\n";

        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexShaderSource);
        glCompileShader(vertexShader);
        checkShaderCompileStatus(vertexShader);

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(fragmentShader);
        checkShaderCompileStatus(fragmentShader);

        int shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);
        checkProgramLinkStatus(shaderProgram);

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        return shaderProgram;
    }

    private void setupMatrices() {
        // Model matrix (identity matrix)
        modelMatrix = BufferUtils.createFloatBuffer(16);
        modelMatrix.put(new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        }).flip();

        // View matrix (look-at matrix)
        viewMatrix = BufferUtils.createFloatBuffer(16);
        viewMatrix.put(new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            1, 0, 1, -4,
            0, 0, 0, 1
        }).flip();

        // Projection matrix (perspective projection)
        projectionMatrix = BufferUtils.createFloatBuffer(16);
        float fov = (float) Math.toRadians(75);
        float aspectRatio = 1200.0f / 800.0f;
        float near = 0.1f;
        float far = 300.0f;
        float yScale = (float) (1 / Math.tan(fov / 2.0));
        float xScale = yScale / aspectRatio;
        projectionMatrix.put(new float[] {
            xScale, 0, 0, 0,
            0, yScale, 0, 0,
            0, 0, (far + near) / (near - far), -1,
            0, 0, (2 * far * near) / (near - far), 0
        }).flip();
    }

    private void setupBuffers() {
        // Generate and bind VAO
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // Generate and bind VBO
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        // Upload vertex data
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.size());
        for (float vertex : vertices) {
            vertexBuffer.put(vertex);
        }
        vertexBuffer.flip();
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // Set vertex attribute pointers
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        // Unbind VAO
        glBindVertexArray(0);
    }

    private void loop() {
        // Set the clear color
        glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Enable depth testing
        glEnable(GL_DEPTH_TEST);

        // Render loop
        while (!glfwWindowShouldClose(window)) {
            // Clear the framebuffer
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Use the shader program
            glUseProgram(shaderProgram);

            // Set the matrices
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "model"), false, modelMatrix);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "view"), false, viewMatrix);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "projection"), false, projectionMatrix);

            // Bind the VAO
            glBindVertexArray(vaoId);

            // Draw the model
            glDrawArrays(GL_TRIANGLES, 0, vertices.size() / 3);

            // Unbind the VAO
            glBindVertexArray(0);

            // Swap the buffers
            glfwSwapBuffers(window);

            // Poll for window events
            glfwPollEvents();
        }
    }

    private void checkShaderCompileStatus(int shader) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader compilation failed: " + glGetShaderInfoLog(shader));
        }
    }

    private void checkProgramLinkStatus(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Program linking failed: " + glGetProgramInfoLog(program));
        }
    }

    public static void main(String[] args) {
        new perpro_3dcar().run();
    }
}