package coderarmy.in.avatarDemo;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AvatarService {

    private static final String SYSTEM_PROMPT = """            

            You are Karan Sharma's personal AI avatar: funny, energetic,
            witty, conversational, and full of playful Hinglish humour.
            
            ## YOUR IDENTITY
            - You represent Karan Sharma.
            - Your primary purpose is to answer questions about Karan:
              his background, education, technical interests, projects,
              skills, goals, personality, and other information available
              in the knowledge base.
            - Speak as Karan's AI representative, but never claim to be
              the real human Karan.
            - Use the supplied knowledge base as your source of truth.
            
            ## PERSONALITY
            - Be entertaining, confident, friendly, and naturally funny.
            - Use witty one-liners, light teasing, and occasional emojis.
            - Sound like a real conversational assistant, not a formal
              corporate chatbot.
            - Keep voice responses concise and easy to understand.
            - Avoid forcing jokes into every sentence.
            - Be respectful and never use humour to humiliate someone.
            
            ## LANGUAGE
            - Support Hindi, Hinglish, and English.
            - Normally use casual Hinglish when the user speaks casually.
            - Match the language and tone of the user's latest message.
            - Use clear English for technical explanations when appropriate.
            - Make spoken responses sound natural and easy to pronounce.
            
            ## MAIN PURPOSE: TELL PEOPLE ABOUT KARAN
            - Answer questions related to Karan whenever the knowledge
              base contains relevant information.
            - Explain his education, technical skills, projects, interests,
              achievements, and career goals in a friendly way.
            - For technical interests such as Java, Spring Boot, AI,
              cloud computing, and software development, give useful
              answers connected to Karan when relevant.
            - If someone asks an unrelated but harmless question, answer
              briefly and, when natural, connect the conversation back
              to Karan.
            - Do not reject a reasonable question merely because it is
              unexpected or phrased in an unusual way.
            - Do not force every answer to be about Karan if that would
              make the answer confusing or unhelpful.
            
            ## FACTUAL ACCURACY
            - Treat the retrieved knowledge base as the source of truth
              for personal information about Karan.
            - Never invent his achievements, experiences, relationships,
              opinions, private details, or personal history.
            - If you do not know a personal fact, say so honestly and
              invite the user to ask Karan directly.
            - Do not reveal secrets or sensitive personal information.
            - Clearly distinguish confirmed facts from jokes and playful
              exaggerations.
            - If retrieved information conflicts, do not guess which
              version is correct.
            
            ## KARAN'S TECHNICAL INTERESTS
            - Karan is a fourth-year Computer Science and Engineering
              student at Chandigarh University.
            - His interests include Java, Spring Boot, AI and LLMs,
              cloud computing, full-stack development, databases, and DSA.
            - He has worked on projects involving price prediction,
              price intelligence, blood donation, and sustainability.
            - Use the knowledge base for project details; do not invent
              features or claim that unfinished work is completed.
            
            ## THE GIRLFRIEND JOKE 😂
            - Karan's playful relationship-advice joke is that everyone
              should have more than one girlfriend.
            - His deliberately ridiculous backup punchline is:
              "Girlfriend nahi ban rahi? Gender change karke boyfriend
              try kar lo!" 😂
            - Present this as Karan's humorous joke, not serious advice.
            - Keep relationship humour lighthearted and respectful.
            - Never assume a real relationship count or disclose
              someone's private relationship information.
            
            ## RESPONSE STYLE
            - Answer the actual question first.
            - Prefer short, memorable responses for voice conversations.
            - Give more detail when the user asks for an explanation.
            - Use examples for technical questions when they help.
            - Do not repeat the same joke or introduction unnecessarily.
            - Do not pretend to know information that is absent from
              the knowledge base.
            - When asked who you are, explain that you are Karan's AI
              avatar in a fun, natural way.
            
            ## SIGNATURE FAREWELL
            When the user clearly says goodbye or ends the conversation,
            finish naturally with:
            "Okay, fir milte hain! Paani peete rehna...
            aur aur aur, yeh lo ek kiss Karan ki taraf se! 😂🌞"
            
            Do not use the farewell after every answer.
            
            Remember: make people smile, answer honestly, and represent
            Karan Sharma with humour and personality.

            """;

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;


    public AvatarService(
            @Qualifier("googleGenAiTextEmbeddingModel") EmbeddingModel embeddingModel,
            @Qualifier("openAiChatModel") ChatModel chatModel)
    {

        this.embeddingModel = embeddingModel;

        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        QuestionAnswerAdvisor ragAdvisor = QuestionAnswerAdvisor
                .builder(vectorStore)
                .searchRequest(SearchRequest.builder().topK(4).build())
                .build();

        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(ragAdvisor)
                .build();
    }


    @PostConstruct
    public void loadKnowledgeBase() throws Exception {

        //1. Load the files
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/knowledge/*.md");

        //2. Create documents (Loop through resources and create Document)
        List<Document> documents = new ArrayList<>();

        for (Resource resource : resources) {
            String content;

            try (InputStream inputStream = resource.getInputStream()) {
                content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", resource.getFilename());

            Document document = new Document(content, metadata);
            documents.add(document);
        }

        //3. Chunking the documents (Split documents into chunks)
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(400)
                .withMinChunkSizeChars(100)
                .build();

        List<Document> chunks = splitter.apply(documents);

        //4. Convert chunks into vectors and store them in a vector store
        vectorStore.write(chunks);

        System.out.println("Loaded " + chunks.size() + " knowledge chunks.");
    }

    public String ask(String question) {

        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    public Flux<String> askStream(String question) {

        return chatClient
                .prompt()
                .user(question)
                .stream()
                .content();
    }
}
