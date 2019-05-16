package controllers.pagemanagement;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.routing.FromConfig;
import akka.routing.RouterConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import controllers.BaseController;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.http.HttpHeaders;
import org.sunbird.actor.router.RequestRouter;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestValidator;
import org.sunbird.learner.util.ContentSearchUtil;
import play.api.http.Writeable;
import play.api.libs.ws.WSClient;
import play.api.libs.ws.WSResponse;
import play.api.mvc.Codec;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;
import scala.Tuple2;
import scala.collection.JavaConverters;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PageAssembleController extends BaseController {
    @Inject
    static
    ActorSystem system;
    static ActorRef actorRef;

    static{
        Config config = ConfigFactory.systemEnvironment().withFallback(ConfigFactory.load());
        Config conf = config.getConfig("SunbirdMWSystem");
        system = ActorSystem.create("SunbirdMWSystem", conf);
        actorRef = system.actorOf(FromConfig.getInstance().props(Props.create(PageSearchActor.class).withDispatcher("page-search-actor-dispatcher")),
                PageSearchActor.class.getSimpleName());
    }


    public Promise<Result> getPageData() {

        try {
            JsonNode requestData = request().body().asJson();
            Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
            RequestValidator.validateGetPageData(reqObj);
            reqObj.setOperation(ActorOperations.GET_PAGE_DATA.getValue());
            reqObj.setRequestId(ExecutionContext.getRequestId());
            reqObj.setEnv(getEnvironment());
            reqObj.getContext().put(JsonKey.URL_QUERY_STRING, getQueryString(request().queryString()));
            reqObj.getRequest().put(JsonKey.CREATED_BY, ctx().flash().get(JsonKey.USER_ID));
            HashMap<String, Object> map = new HashMap<>();
            map.put(JsonKey.PAGE, reqObj.getRequest());
            map.put(JsonKey.HEADER, getAllRequestHeaders(request()));
            reqObj.setRequest(map);

            return Promise.wrap(Patterns.ask((ActorRef) getActorRef(), reqObj, timeout)).map(new Function<Object, Promise<Result>>() {
                @Override
                public Promise<Result> apply(Object o) throws Throwable {
                    if (o instanceof Response) {
                        Response res = (Response) o;
                        Map<String, Object> resResponse = (Map<String, Object>) res.getResult().get("response");
                        if(MapUtils.isNotEmpty(resResponse)){
                            List<Map<String, Object>> sections = (List<Map<String, Object>>) resResponse.get("sections");
                            Request req = new Request();
                            req.setOperation("getSearchData");
                            req.setRequestId(ExecutionContext.getRequestId());
                            req.setEnv(getEnvironment());
                            req.getRequest().put("sections", sections);
                            return actorResponseHandler(actorRef, req, timeout, null, request());
                        }
                        else{
                            return Promise.pure(createCommonExceptionResponse(new Exception(), request()));
                        }
                    } else if (o instanceof Exception) {
                        return Promise.pure(createCommonExceptionResponse((Exception) o, request()));
                    } else {
                        return Promise.pure(createCommonExceptionResponse(new Exception(), request()));
                    }

                }
            }).flatMap(new Function<Promise<Result>, Promise<Result>>() {
                @Override
                public Promise<Result> apply(Promise<Result> resultPromise) throws Throwable {
                    return resultPromise;
                }
            });
        } catch (Exception e) {
            ProjectLogger.log(
                    "PageController:getPageData: Exception occurred with error message = " + e.getMessage(),
                    LoggerEnum.ERROR.name());
            return  Promise.<Result>pure(createCommonExceptionResponse(e, request()));
        }
    }
}
