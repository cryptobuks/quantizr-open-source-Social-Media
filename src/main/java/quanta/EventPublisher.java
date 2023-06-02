package quanta;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher implements ApplicationEventPublisherAware {
	
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventPublisher.class);
	// NOT autowired (this is correct)
	private ApplicationEventPublisher publisher;

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	public ApplicationEventPublisher getPublisher() {
		return publisher;
	}
}
