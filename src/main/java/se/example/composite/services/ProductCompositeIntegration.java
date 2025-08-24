package se.example.composite.services;

import static java.util.logging.Level.FINE;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import se.example.api.core.product.Product;
import se.example.api.core.product.ProductService;
import se.example.api.core.recommendation.Recommendation;
import se.example.api.core.recommendation.RecommendationService;
import se.example.api.core.review.Review;
import se.example.api.core.review.ReviewService;
import se.example.api.event.Event;
import se.example.api.exception.InvalidInputException;
import se.example.api.exception.NotFoundException;
import se.example.util.http.HttpErrorInfo;

@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {

  private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);

  private final WebClient webClient;
  private final ObjectMapper mapper;

  private final String productServiceUrl;
  private final String recommendationServiceUrl;
  private final String reviewServiceUrl;

  private final Scheduler publishEventScheduler;   

  private final  StreamBridge streamBridge;

  @Autowired
  public ProductCompositeIntegration(
    WebClient.Builder webClient,
    ObjectMapper mapper,
      StreamBridge streamBridge,
      Scheduler publishEventScheduler,
      @Value("${app.product-service.host}") String productServiceHost,
      @Value("${app.product-service.port}") int productServicePort,
      @Value("${app.recommendation-service.host}") String recommendationServiceHost,
      @Value("${app.recommendation-service.port}") int recommendationServicePort,
      @Value("${app.review-service.host}") String reviewServiceHost,
      @Value("${app.review-service.port}") int reviewServicePort) {

    this.webClient = webClient.build();
    this.mapper = mapper;
    this.streamBridge = streamBridge;
    this.publishEventScheduler = publishEventScheduler;

    productServiceUrl = "http://" + productServiceHost + ":" + productServicePort;
    recommendationServiceUrl = "http://" + recommendationServiceHost + ":" + recommendationServicePort;
    reviewServiceUrl = "http://" + reviewServiceHost + ":" + reviewServicePort;
  }

  @Override
  public Mono<Product> createProduct(Product body) {

    return Mono.fromCallable(() -> {
      sendMessage("products-out-0", new Event<>(Event.EventType.CREATE, body.getProductId(), body));
      return body;
    }).subscribeOn(publishEventScheduler);

  }

  @Override
  public Mono<Product> getProduct(int productId) {

    String url = productServiceUrl + "/product/" + productId;
    LOG.debug("Will call the getProduct API on URL: {}", url);

     Mono<Product> result  = webClient.get().uri(url).retrieve().bodyToMono(Product.class).log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, ex -> handleException(ex));
        return result;
  }

  @Override
  public Mono<Void> deleteProduct(int productId) {

    return Mono.fromRunnable(() -> {
      sendMessage("products-out-0", new Event<>(Event.EventType.DELETE, productId, null));
    }).subscribeOn(publishEventScheduler).then();

  }

  @Override
  public Mono<Recommendation> createRecommendation(Recommendation body) {

    return Mono.fromCallable(() -> {
      sendMessage("recommendations-out-0", new Event<>(Event.EventType.CREATE, body.getProductId(), body));
      return body;
    }).subscribeOn(publishEventScheduler);
  }

  @Override
  public Flux<Recommendation> getRecommendations(int productId) {
    String url = recommendationServiceUrl + "/recommendation"+"?productId=" + productId;
    LOG.debug("Will call the getRecommendations API on URL: {}", url);
      Flux<Recommendation> result  =webClient.get()
        .uri(url)
        .retrieve()
        .bodyToFlux(Recommendation.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, this::handleException);
        return result;
  }

  @Override
  public Mono<Void> deleteRecommendations(int productId) {
   return Mono.fromRunnable(() -> {
      sendMessage("recommendations-out-0", new Event<>(Event.EventType.DELETE, productId, null));
    }).subscribeOn(publishEventScheduler).then();
  }

  @Override
  public Mono<Review> createReview(Review body) {
    return Mono.fromCallable(() -> {
      sendMessage("reviews-out-0", new Event<>(Event.EventType.CREATE, body.getProductId(), body));
      return body;
    }).subscribeOn(publishEventScheduler);
  }

  @Override
  public Flux<Review> getReviews(int productId) {

    String url = reviewServiceUrl + "/review"+"?productId=" + productId;
    LOG.debug("Will call the getReviews API on URL: {}", url);
       Flux<Review> result = webClient.get()
        .uri(url)
        .retrieve()
        .bodyToFlux(Review.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, this::handleException);
        return result;
  }

  @Override
  public Mono<Void> deleteReviews(int productId) {
   return Mono.fromRunnable(() -> {
      sendMessage("reviews-out-0", new Event<>(Event.EventType.DELETE, productId, null));
    }).subscribeOn(publishEventScheduler).then();
  }


   private void sendMessage(String bindingName, Event event) {
    LOG.debug("Sending a {} message to {}", event.getEventType(), bindingName);
    Message message = MessageBuilder.withPayload(event)
      .setHeader("partitionKey", event.getKey())
      .build();
    streamBridge.send(bindingName, message);
  }




  private Throwable handleException(Throwable ex) {

    if (!(ex instanceof WebClientResponseException)) {
      LOG.warn("Got a unexpected error: {}, will rethrow it", ex.toString());
      return ex;
    }

    WebClientResponseException wcre = (WebClientResponseException) ex;

    switch (HttpStatus.resolve(wcre.getStatusCode().value())) {

      case NOT_FOUND:
        return new NotFoundException(getErrorMessage(wcre));

      case UNPROCESSABLE_ENTITY:
        return new InvalidInputException(getErrorMessage(wcre));

      default:
        LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", wcre.getStatusCode());
        LOG.warn("Error body: {}", wcre.getResponseBodyAsString());
        return ex;
    }
  }

  private String getErrorMessage(WebClientResponseException ex) {
    try {
      return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
    } catch (IOException ioex) {
      return ex.getMessage();
    }
  }

}