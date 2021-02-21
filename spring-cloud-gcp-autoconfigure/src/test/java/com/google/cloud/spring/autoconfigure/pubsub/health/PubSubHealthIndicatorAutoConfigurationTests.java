/*
 * Copyright 2018-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spring.autoconfigure.pubsub.health;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import com.google.cloud.spring.autoconfigure.pubsub.GcpPubSubAutoConfiguration;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.junit.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.NamedContributor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for Pub/Sub Health Indicator autoconfiguration.
 *
 * @author Elena Felder
 */
public class PubSubHealthIndicatorAutoConfigurationTests {

	private ApplicationContextRunner baseContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(PubSubHealthIndicatorAutoConfiguration.class,
					GcpPubSubAutoConfiguration.class))
			.withBean(GcpProjectIdProvider.class,  () -> () -> "fake project")
			.withBean(CredentialsProvider.class, () -> () -> mock(Credentials.class));

	@Test
	public void healthIndicatorPresent() {
		this.baseContextRunner
				.withPropertyValues(
						"management.health.pubsub.enabled=true",
						"spring.cloud.gcp.pubsub.health.subscription=test",
						"spring.cloud.gcp.pubsub.health.timeout-millis=1500")
				.run(ctx -> {
					PubSubHealthIndicator healthIndicator = ctx.getBean(PubSubHealthIndicator.class);
					assertThat(healthIndicator).isNotNull();
				});
	}

	@Test
	public void compositeHealthIndicatorPresentMultiplePubSubTemplate() {
		PubSubTemplate mockPubSubTemplate1 = mock(PubSubTemplate.class);
		PubSubTemplate mockPubSubTemplate2 = mock(PubSubTemplate.class);

		this.baseContextRunner
				.withBean("pubSubTemplate1", PubSubTemplate.class, () -> mockPubSubTemplate1)
				.withBean("pubSubTemplate2", PubSubTemplate.class, () -> mockPubSubTemplate2)
				.withPropertyValues(
						"management.health.pubsub.enabled=true",
						"spring.cloud.gcp.pubsub.health.subscription=test",
						"spring.cloud.gcp.pubsub.health.timeout-millis=1500")
				.run(ctx -> {
					assertThatThrownBy(() -> ctx.getBean(PubSubHealthIndicator.class))
							.isInstanceOf(NoSuchBeanDefinitionException.class);
					CompositeHealthContributor healthContributor = ctx.getBean("pubSubHealthContributor", CompositeHealthContributor.class);
					assertThat(healthContributor).isNotNull();
					assertThat(healthContributor.stream()).hasSize(2);
					assertThat(healthContributor.stream().map(c -> ((NamedContributor) c).getName()))
							.containsExactlyInAnyOrder("pubSubTemplate1", "pubSubTemplate2");
				});
	}

	@Test
	public void healthCheckConfigurationBacksOffWhenHealthIndicatorBeanPresent() {
		PubSubHealthIndicator userHealthIndicator = mock(PubSubHealthIndicator.class);

		this.baseContextRunner
				.withBean("pubSubTemplate1", PubSubTemplate.class, () -> mock(PubSubTemplate.class))
				.withBean("pubSubTemplate2", PubSubTemplate.class, () -> mock(PubSubTemplate.class))
				.withBean(PubSubHealthIndicator.class, () -> userHealthIndicator)
				.withPropertyValues("management.health.pubsub.enabled=true")
				.run(ctx -> {
					assertThat(ctx).doesNotHaveBean("pubSubHealthContributor");
					assertThat(ctx).hasSingleBean(PubSubHealthIndicator.class);
					assertThat(ctx.getBean(PubSubHealthIndicator.class)).isEqualTo(userHealthIndicator);
				});
	}

	@Test
	public void healthIndicatorDisabledWhenPubSubTurnedOff() {
		this.baseContextRunner
				.withPropertyValues(
						"management.health.pubsub.enabled=true",
						"spring.cloud.gcp.pubsub.enabled=false")
				.run(ctx -> {
					assertThat(ctx.getBeansOfType(PubSubHealthIndicator.class)).isEmpty();
				});
	}

}
