package io.durbs.places.elasticsearch

import com.google.inject.Inject
import com.google.inject.Singleton
import com.lambdaworks.redis.GeoWithin
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.durbs.places.GlobalConfig
import io.durbs.places.Place
import io.durbs.places.PlaceService
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.unit.DistanceUnit
import rx.Observable
import rx.functions.Func1

import static org.elasticsearch.index.query.QueryBuilders.geoDistanceQuery

@Singleton
@CompileStatic
@Slf4j
class ElasticsearchPlaceService implements PlaceService {

  @Inject
  ElasticsearchConfig elasticsearchConfig

  @Inject
  Client elasticSearchClient

  @Inject
  GlobalConfig globalConfig

  @Override
  Observable<Integer> insertPlace(final Place place) {

    Observable.from(elasticSearchClient.index(new IndexRequest(elasticsearchConfig.index, elasticsearchConfig.type).source(ElasticsearchConversionFunctions.CREATE_OBJECT_BUILDER_FOR_PLACE(place))))
      .map({ IndexResponse indexResponse ->

      indexResponse.created ? 1 : 0
    } as Func1)
  }

  @Override
  Observable<Place> getPlaces(final Double latitude, final Double longitude, final Double searchRadius) {

    final String query = "{ \"query\" : { \"match_all\" : {} }, \"filter\" : { \"geo_distance\" : { \"distance\" : \"${searchRadius}m\", \"location\" : { \"lat\" : ${latitude}, \"lon\" : ${longitude} } } } }"

    final SearchRequestBuilder builder = elasticSearchClient
      .prepareSearch(elasticsearchConfig.index)
      .setTypes(elasticsearchConfig.type)
      .setQuery(geoDistanceQuery('location')
        .distance(searchRadius, DistanceUnit.METERS)
        .lat(latitude)
        .lon(longitude))
      .setSize(globalConfig.resultSetSize as Integer)

    Observable.from(elasticSearchClient.search(new SearchRequest(elasticsearchConfig.index).types(elasticsearchConfig.type).source(query)))
//    Observable.from(builder.execute())
      .flatMap({ final SearchResponse response ->

      Observable.from(response.hits.hits)
    } as Func1)
      .map(ElasticsearchConversionFunctions.MAP_SEARCH_HIT_TO_PLACE)
  }

  @Override
  Observable<GeoWithin<Place>> getPlacesWithDistance(final Double latitude, final Double longitude, final Double searchRadius) {
    return null
  }

}
