package org.pentaho.di.sdk.samples.steps.ntmlclient.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonUtils {
    private JsonUtils() {
        throw new AssertionError();
    }

    public static boolean isJSONArray( Object obj ) {
        return obj.getClass().equals( JSONArray.class );
    }

    public static List<String> getKey( JSONArray array ) {
        JSONObject jsonObject = JSON.parseObject( array.get(0).toString() );

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Object>  entry : jsonObject.entrySet()) {
            result.add( entry.getKey() );
        }
        return result;
    }

}
