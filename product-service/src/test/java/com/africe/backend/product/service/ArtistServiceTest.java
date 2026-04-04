package com.africe.backend.product.service;

import com.africe.backend.common.dto.ArtistResponse;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Artist;
import com.africe.backend.product.repository.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistServiceTest {

    @Mock ArtistRepository artistRepository;
    @InjectMocks ArtistService artistService;

    @Test
    void listAll_returnsAllArtists() {
        Artist artist = Artist.builder()
                .id("a1").slug("mc-artist").name("MC Artist")
                .bio("Bio").image("img.jpg")
                .socialLinks(Map.of("instagram", "@mc"))
                .build();
        when(artistRepository.findAll()).thenReturn(List.of(artist));

        List<ArtistResponse> result = artistService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).slug()).isEqualTo("mc-artist");
        assertThat(result.get(0).name()).isEqualTo("MC Artist");
    }

    @Test
    void getBySlug_found() {
        Artist artist = Artist.builder()
                .id("a1").slug("mc-artist").name("MC Artist").build();
        when(artistRepository.findBySlug("mc-artist")).thenReturn(Optional.of(artist));

        ArtistResponse response = artistService.getBySlug("mc-artist");

        assertThat(response.id()).isEqualTo("a1");
    }

    @Test
    void getBySlug_notFound_throws() {
        when(artistRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.getBySlug("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
