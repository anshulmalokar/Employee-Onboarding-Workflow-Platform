package com.onboarding.orchestrator.controller;

import com.onboarding.orchestrator.admin.DlqRecord;
import com.onboarding.orchestrator.admin.DlqReplayResponse;
import com.onboarding.orchestrator.admin.DlqReplayService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dlq")
public class DlqAdminController {

    private final DlqReplayService replayService;

    public DlqAdminController(DlqReplayService replayService) {
        this.replayService = replayService;
    }

    @GetMapping
    public List<DlqRecord> listRecords() {
        return replayService.list();
    }

    @PostMapping("/replay/{recordId}")
    public DlqReplayResponse replayOne(@PathVariable("recordId") long recordId) {
        return replayService.replay(recordId);
    }

    @PostMapping("/replay-all")
    public List<DlqReplayResponse> replayAll(@RequestParam(required = false) String sourceTopic) {
        return replayService.replayAll(sourceTopic);
    }
}
