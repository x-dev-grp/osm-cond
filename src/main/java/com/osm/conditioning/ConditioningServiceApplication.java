package com.osm.conditioning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.envers.repository.support.EnversRevisionRepositoryFactoryBean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.osm.conditioning", "com.xdev", "com.xdev.communicator", "com.xdev.xdevbase", "com.xdev.xdevsecurity"})
@EnableFeignClients(basePackages = {"com.osm.conditioning", "com.xdev", "com.xdev.communicator", "com.xdev.xdevsecurity"})
@EnableJpaRepositories(basePackages = {"com.osm.conditioning", "com.xdev", "com.xdev.xdevbase"},
        repositoryFactoryBeanClass = EnversRevisionRepositoryFactoryBean.class
)
public class ConditioningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConditioningServiceApplication.class, args);
    }

}
