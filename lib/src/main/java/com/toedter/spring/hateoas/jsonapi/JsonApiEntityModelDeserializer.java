/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.toedter.spring.hateoas.jsonapi;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Links;
import org.springframework.util.Assert;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.toedter.spring.hateoas.jsonapi.ReflectionUtils.getAllDeclaredFields;

class JsonApiEntityModelDeserializer extends AbstractJsonApiModelDeserializer<EntityModel<?>>
        implements ContextualDeserializer {

    public static final String CANNOT_DESERIALIZE_INPUT_TO_ENTITY_MODEL = "Cannot deserialize input to EntityModel";

    public JsonApiEntityModelDeserializer() {
        super();
    }

    protected JsonApiEntityModelDeserializer(JavaType contentType) {
        super(contentType);
    }

    @Override
    protected EntityModel<?> convertToRepresentationModel(List<Object> resources, JsonApiDocument doc) {
        Assert.notNull(doc, "JsonApiDocument must not be null!");
        Links links = doc.getLinks();
        if (resources.size() == 1) {
            EntityModel<Object> entityModel = EntityModel.of(resources.get(0));
            if (links != null) {
                entityModel.add(links);
            }

            if (doc.getData() == null) {
                return entityModel;
            }

            @SuppressWarnings("unchecked")
            HashMap<String, Object> relationships =
                    (HashMap<String, Object>) ((HashMap<String, Object>) doc.getData()).get("relationships");

            if (relationships != null) {

                Object object = entityModel.getContent();
                @SuppressWarnings("ConstantConditions")
                final Field[] declaredFields = getAllDeclaredFields(object.getClass());
                for (Field field : declaredFields) {
                    field.setAccessible(true);
                    JsonApiRelationships relationshipsAnnotation = field.getAnnotation(JsonApiRelationships.class);
                    if (relationshipsAnnotation != null) {
                        Object relationship = relationships.get(relationshipsAnnotation.value());
                        try {
                            final Type genericType = field.getGenericType();
                            if (genericType instanceof ParameterizedType) {
                                ParameterizedType type = (ParameterizedType) genericType;
                                if (List.class.isAssignableFrom(field.getType())) {
                                    List<Object> relationshipList = new ArrayList<>();
                                    Object data = ((HashMap<?, ?>) relationship).get("data");
                                    List<HashMap<String, String>> jsonApiRelationships;
                                    if (data instanceof List) {
                                        @SuppressWarnings("unchecked")
                                        List<HashMap<String, String>> castedData = (List<HashMap<String, String>>) data;
                                        jsonApiRelationships = castedData;
                                    } else if (data instanceof HashMap) {
                                        @SuppressWarnings("unchecked")
                                        HashMap<String, String> castedData = (HashMap<String, String>) data;
                                        jsonApiRelationships = Collections.singletonList(castedData);
                                    } else {
                                        throw new IllegalArgumentException(CANNOT_DESERIALIZE_INPUT_TO_ENTITY_MODEL);
                                    }
                                    Type typeArgument = type.getActualTypeArguments()[0];

                                    for (HashMap<String, String> entry : jsonApiRelationships) {
                                        Class<?> typeArgClass = (Class<?>) typeArgument;
                                        Object newInstance = typeArgClass.getDeclaredConstructor().newInstance();
                                        JsonApiResource.setJsonApiResourceFieldAttributeForObject(
                                                newInstance, JsonApiResource.JsonApiResourceField.id, entry.get("id"));
                                        relationshipList.add(newInstance);
                                    }

                                    field.set(object, relationshipList);
                                }
                            }
                        } catch (Exception e) {
                            throw new IllegalArgumentException(CANNOT_DESERIALIZE_INPUT_TO_ENTITY_MODEL);
                        }
                    }
                }
            }
            return entityModel;
        }
        throw new IllegalArgumentException(CANNOT_DESERIALIZE_INPUT_TO_ENTITY_MODEL);

    }

    protected JsonDeserializer<?> createJsonDeserializer(JavaType type) {
        return new JsonApiEntityModelDeserializer(type);
    }
}
