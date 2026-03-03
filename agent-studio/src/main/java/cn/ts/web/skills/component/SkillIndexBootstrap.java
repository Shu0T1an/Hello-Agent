package cn.ts.web.skills.component;

import cn.ts.web.skills.config.SkillsProperties;
import cn.ts.web.skills.service.SkillRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SkillIndexBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(SkillIndexBootstrap.class);

    private final SkillsProperties properties;
    private final SkillRegistryService registryService;

    public SkillIndexBootstrap(SkillsProperties properties, SkillRegistryService registryService) {
        this.properties = properties;
        this.registryService = registryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isEnabled()) {
            logger.info("Skill index bootstrap skipped because agent.skills.enabled=false");
            return;
        }
        SkillRegistryService.ReindexResult result = registryService.reindex();
        logger.info("Skill index initialized. skills={}, roots={}", result.count(), result.roots());
    }
}

