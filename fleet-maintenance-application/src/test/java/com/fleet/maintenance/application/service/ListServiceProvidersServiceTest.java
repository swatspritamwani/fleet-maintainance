package com.fleet.maintenance.application.service;

import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.domain.port.ServiceProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListServiceProvidersServiceTest {

    @Mock
    private ServiceProviderRepository serviceProviderRepository;

    @InjectMocks
    private ListServiceProvidersService service;

    @Test
    void listActive_returnsAllActiveProviders() {
        ServiceProvider provider = new ServiceProvider(
            UUID.randomUUID(), "AutoFix", "auto@fix.com", "555-0100", true);
        when(serviceProviderRepository.findAllActive()).thenReturn(List.of(provider));

        List<ServiceProvider> result = service.listActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("AutoFix");
    }

    @Test
    void listActive_returnsEmptyListWhenNoProviders() {
        when(serviceProviderRepository.findAllActive()).thenReturn(List.of());

        List<ServiceProvider> result = service.listActive();

        assertThat(result).isEmpty();
    }
}
