package com.example.abac_spike;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Base64;

import javax.persistence.EntityManager;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import be.heydari.lib.converters.protobuf.ProtobufUtils;
import be.heydari.lib.converters.protobuf.generated.PDisjunction;
import be.heydari.lib.expressions.Disjunction;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.support.Repositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.util.UrlPathHelper;

import internal.org.springframework.content.rest.utils.RepositoryUtils;

@Configuration
public class AbacConfiguration {

    @Bean
    public ABACRequestFilter abacFilter(Repositories repos, EntityManager em) {
        return new ABACRequestFilter(repos, em);
    }

    @Bean
    public FilterRegistrationBean<ABACRequestFilter> abacFilterRegistration(Repositories repos, EntityManager em) {
        FilterRegistrationBean<ABACRequestFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(abacFilter(repos, em));
        registrationBean.addUrlPatterns("/*");

        return registrationBean;
    }

    @Bean
    public QueryAugmentingABACAspect abacAspect(EntityManager em, PlatformTransactionManager ptm) {
        return new QueryAugmentingABACAspect(em, ptm);
    }

    public static class ABACRequestFilter implements Filter {

        private final Repositories repos;
        private final EntityManager em;

        public ABACRequestFilter(Repositories repos, EntityManager em) {
            this.repos = repos;
            this.em = em;
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
                throws IOException,
                ServletException {

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

            // Emad
            String abacContext = request.getHeader("X-ABAC-Context");
            if (abacContext != null) {
                byte[] abacContextProtobytes = Base64.getDecoder().decode(abacContext);
                PDisjunction pDisjunction = PDisjunction.newBuilder().mergeFrom(abacContextProtobytes).build();
                Disjunction disjunction = ProtobufUtils.to(pDisjunction, "");
                ABACContext.setCurrentAbacContext(disjunction);
            }

            filterChain.doFilter(servletRequest, servletResponse);

            ABACContext.clear();
            EntityContext.clear();
        }
    }
}
