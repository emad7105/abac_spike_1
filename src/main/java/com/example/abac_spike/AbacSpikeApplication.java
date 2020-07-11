package com.example.abac_spike;

import internal.org.springframework.content.rest.utils.RepositoryUtils;
import org.apache.batik.util.Platform;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.content.fs.config.FilesystemStoreConfigurer;
import org.springframework.content.rest.config.ContentRestConfigurer;
import org.springframework.content.rest.config.RestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.EnableLoadTimeWeaving;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.support.Repositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.util.UrlPathHelper;

import javax.persistence.EntityManager;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;

import static java.lang.String.format;

@SpringBootApplication
@EnableAspectJAutoProxy()
@EnableJpaRepositories()
public class AbacSpikeApplication {

	public static void main(String[] args) {
		SpringApplication.run(AbacSpikeApplication.class, args);
	}

	@Configuration
	public static class Config {

		@Bean
		public RequestFilter abacFilter(Repositories repos, EntityManager em) {
			return new RequestFilter(repos, em);
		}

		@Bean
		public FilterRegistrationBean<RequestFilter> abacFilterRegistration(Repositories repos, EntityManager em){
			FilterRegistrationBean<RequestFilter> registrationBean = new FilterRegistrationBean<>();

			registrationBean.setFilter(abacFilter(repos, em));
			registrationBean.addUrlPatterns("/*");

			return registrationBean;
		}

		@Bean
		public ContentRestConfigurer restConfigurer() {
			return new ContentRestConfigurer() {
				@Override
				public void configure(RestConfiguration config) {
					config.setBaseUri(URI.create("/content"));
				}
			};
		}

		@Bean
		public QueryAugmentingAspect documentRepoAbacAspect(EntityManager em, PlatformTransactionManager ptm) {
			return new QueryAugmentingAspect(em, ptm);
		}
	}

	public static class RequestFilter implements Filter {

		private final Repositories repos;
		private final EntityManager em;

		public RequestFilter(Repositories repos, EntityManager em) {
			this.repos = repos;
			this.em = em;
		}

		@Override
		public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
			HttpServletRequest request = (HttpServletRequest) servletRequest;

			String path = new UrlPathHelper().getLookupPathForRequest(request);
			String[] pathElements = path.split("/");
			RepositoryInformation ri = RepositoryUtils.findRepositoryInformation(repos, pathElements[1]);
			if (ri == null) {
				ri = RepositoryUtils.findRepositoryInformation(repos, pathElements[2]);
			}
			if (ri == null) {
				throw new IllegalStateException(format("Unable to resolve entity class: %s", path));
			}
			Class<?> entityClass = ri.getDomainType();

			EntityInformation ei = JpaEntityInformationSupport.getEntityInformation(entityClass, em);
			if (entityClass != null) {
				EntityContext.setCurrentEntityContext(ei);
			}

			String tenantID = request.getHeader("X-ABAC-Context");
			if (tenantID != null) {
				AbacContext.setCurrentAbacContext(tenantID);
			}

			filterChain.doFilter(servletRequest, servletResponse);

			AbacContext.clear();
			EntityContext.clear();
		}
	}

	public static class EntityContext {

		private static ThreadLocal<EntityInformation> currentEntityContext = new InheritableThreadLocal<>();

		public static EntityInformation getCurrentEntityContext() {
			return currentEntityContext.get();
		}

		public static void setCurrentEntityContext(EntityInformation ei) {
			currentEntityContext.set(ei);
		}

		public static void clear() {
			currentEntityContext.set(null);
		}
	}

	public static class AbacContext {

		private static ThreadLocal<String> currentAbacContext = new InheritableThreadLocal<>();

		public static String getCurrentAbacContext() {
			return currentAbacContext.get();
		}

		public static void setCurrentAbacContext(String tenant) {
			currentAbacContext.set(tenant);
		}

		public static void clear() {
			currentAbacContext.set(null);
		}
	}
}
