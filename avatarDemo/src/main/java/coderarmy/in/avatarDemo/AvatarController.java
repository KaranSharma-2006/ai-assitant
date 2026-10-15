package coderarmy.in.avatarDemo;

import coderarmy.in.avatarDemo.dto.AskRequest;
import coderarmy.in.avatarDemo.dto.AskResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/avatar")
public class AvatarController {

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        String answer = avatarService.ask(request.getMessage());
        return new AskResponse(answer);
    }

    @PostMapping(
            value = "/ask/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<String> askStream(@RequestBody AskRequest request) {
        return avatarService.askStream(request.getMessage());
    }
}
