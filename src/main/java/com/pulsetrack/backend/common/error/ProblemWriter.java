package com.pulsetrack.backend.common.error;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Ecrit un {@code application/problem+json} directement dans la reponse.
 *
 * <p>Necessaire pour tout ce qui est refuse <strong>avant</strong> d'atteindre un
 * controleur : la chaine de filtres de securite n'a pas de
 * {@code @RestControllerAdvice} devant elle, et sans ce relais elle rend un corps
 * vide. Un client mobile ne peut alors pas distinguer une session expiree d'une
 * panne reseau, et affiche « une erreur est survenue » dans les deux cas.
 *
 * <p>Le format est exactement celui de {@link ApiExceptionHandler} : meme
 * enveloppe RFC 9457, meme espace de noms de {@code type}. Le client n'a qu'une
 * seule forme d'erreur a savoir lire.
 */
@Component
public class ProblemWriter {

    /**
     * Espace de noms des types de probleme. Partage avec
     * {@link ApiExceptionHandler} : deux prefixes differents obligeraient le
     * client a connaitre deux familles d'URL.
     */
    public static final String PROBLEM_BASE = "https://pulsetrack.app/problems/";

    private final ObjectMapper objectMapper;

    public ProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param slug       dernier segment du {@code type}, qui est ce sur quoi le
     *                   client route sa reaction
     * @param properties proprietes supplementaires, portees a la racine du
     *                   document comme le permet la RFC
     */
    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      HttpStatus status,
                      String title,
                      String detail,
                      String slug,
                      Map<String, Object> properties) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + slug));
        problem.setInstance(URI.create(request.getRequestURI()));
        properties.forEach(problem::setProperty);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      HttpStatus status,
                      String title,
                      String detail,
                      String slug) throws IOException {
        write(request, response, status, title, detail, slug, Map.of());
    }
}
