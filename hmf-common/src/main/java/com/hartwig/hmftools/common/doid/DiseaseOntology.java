package com.hartwig.hmftools.common.doid;

import static com.hartwig.hmftools.common.utils.json.JsonFunctions.nullableString;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.optionalJsonArray;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.optionalJsonObject;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.optionalString;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.optionalStringList;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.string;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.stringList;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.hartwig.hmftools.common.utils.json.JsonDatamodelChecker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DiseaseOntology {

    private DiseaseOntology() {
    }

    private static final Logger LOGGER = LogManager.getLogger(DiseaseOntology.class);

    @NotNull
    public static DoidEntry readDoidJsonFile(@NotNull String doidJsonFile) throws IOException {
        JsonParser parser = new JsonParser();
        JsonReader reader = new JsonReader(new FileReader(doidJsonFile));
        reader.setLenient(true);
        List<DoidNode> doidNodes = Lists.newArrayList();
        ImmutableDoidEntry.Builder doidDoidEntryBuilder = ImmutableDoidEntry.builder();

        while (reader.peek() != JsonToken.END_DOCUMENT) {
            JsonObject doidObject = parser.parse(reader).getAsJsonObject();
            if (doidObject.size() == 1) {
                JsonDatamodelChecker doidObjectChecker = DoidDatamodelCheckerFactory.doidObjectChecker();
                doidObjectChecker.check(doidObject);

                JsonArray graphArray = doidObject.getAsJsonArray("graphs");
                for (JsonElement graph : graphArray) {
                    JsonDatamodelChecker doidGraphsChecker = DoidDatamodelCheckerFactory.doidGraphsChecker();
                    doidGraphsChecker.check(graph.getAsJsonObject());

                    // Extract doid graph
                    DoidGraphMetaData graphMetaData = createMetaNodes(optionalJsonObject(graph.getAsJsonObject(), "meta"));
                    List<DoidLogicalDefinitionAxioms> doidLogicalDefinitionAxioms =
                            extractDoidLogicalDefinitionAxioms(graph.getAsJsonObject().getAsJsonArray("logicalDefinitionAxioms"));

                    // Extract doid edges
                    DoidEdges result = new DoidEdges();
                    JsonArray edgesArray = graph.getAsJsonObject().getAsJsonArray("edges");
                    for (JsonElement edgeElement : edgesArray) {
                        JsonObject edge = edgeElement.getAsJsonObject();
                        JsonDatamodelChecker doidEdgeChecker = DoidDatamodelCheckerFactory.doidEdgeChecker();
                        doidEdgeChecker.check(edge);

                        String predicate = string(edge, "pred");
                        if (predicate.equals("is_a")) {
                            String child = extractDoid(string(edge, "sub"));
                            String parent = extractDoid(string(edge, "obj"));
                            result.isA(child, parent);
                        }
                    }

                    // Extract doid nodes
                    JsonArray nodeArray = graph.getAsJsonObject().getAsJsonArray("nodes");
                    for (JsonElement nodeElement : nodeArray) {
                        JsonObject node = nodeElement.getAsJsonObject();
                        JsonDatamodelChecker doidNodesChecker = DoidDatamodelCheckerFactory.doidNodesChecker();
                        doidNodesChecker.check(node);
                        String url = string(node, "id");
                        doidNodes.add(ImmutableDoidNode.builder()
                                .doid(extractDoid(url))
                                .url(url)
                                .doidMetadata(extractDoidMetadata(optionalJsonObject(node, "meta")))
                                .type(optionalString(node, "type"))
                                .doidTerm(optionalString(node, "lbl"))
                                .build());
                    }

                    // Add data to doid entry
                    doidDoidEntryBuilder.doidNodes(doidNodes);
                    doidDoidEntryBuilder.edges(result);
                    doidDoidEntryBuilder.id(string(graph.getAsJsonObject(), "id"));
                    doidDoidEntryBuilder.meta(graphMetaData);
                    doidDoidEntryBuilder.equivalentNodesSets(optionalStringList(graph.getAsJsonObject(), "equivalentNodesSets")); // is always empty string list
                    doidDoidEntryBuilder.logicalDefinitionAxioms(doidLogicalDefinitionAxioms);
                    doidDoidEntryBuilder.domainRangeAxioms(optionalStringList(graph.getAsJsonObject(), "domainRangeAxioms")); // is always empty string list
                    doidDoidEntryBuilder.propertyChainAxioms(optionalStringList(graph.getAsJsonObject(), ("propertyChainAxioms"))); // is always empty string list
                }
            } else {
                LOGGER.error(" Size {} of graph elements are not correct!", doidObject.size());
            }
        }

        return doidDoidEntryBuilder.build();
    }

    @Nullable
    private static DoidGraphMetaData createMetaNodes(@Nullable JsonObject metaGraphObject) {

        //TODO check
        JsonArray xrefArray = metaGraphObject.getAsJsonArray("xrefs");
        List<DoidXref> xrefValList = Lists.newArrayList();
        if (xrefArray != null) {
            JsonDatamodelChecker xrefChecker = DoidDatamodelCheckerFactory.doidMetadataXrefChecker();

            for (JsonElement xref : xrefArray) {
                xrefChecker.check(xref.getAsJsonObject());
                xrefValList.add(ImmutableDoidXref.builder().val(string(xref.getAsJsonObject(), "val")).build());
            }
        }

        ImmutableDoidGraphMetaData.Builder DoidGraphMetaDataBuilder = ImmutableDoidGraphMetaData.builder();
        DoidGraphMetaDataBuilder.basicPropertyValues(extractBasicPropertyValues(optionalJsonArray(metaGraphObject, "basicPropertyValues")));
        DoidGraphMetaDataBuilder.subsets(optionalStringList(metaGraphObject, "subsets"));
        DoidGraphMetaDataBuilder.xrefs(xrefValList);
        DoidGraphMetaDataBuilder.version(optionalString(metaGraphObject, "version"));
        return DoidGraphMetaDataBuilder.build();
    }

    @Nullable
    private static List<DoidBasicPropertyValue> extractBasicPropertyValues(@Nullable JsonArray basicPropertyValueArray) {
        //TODO check
        if (basicPropertyValueArray == null) {
            return null;
        }

        List<DoidBasicPropertyValue> doidBasicPropertyValueList = Lists.newArrayList();
        JsonDatamodelChecker basicPropertyValuesChecker = DoidDatamodelCheckerFactory.doidBasicPropertyValuesChecker();

        for (JsonElement basicPropertyElement : basicPropertyValueArray) {
            JsonObject basicProperty = basicPropertyElement.getAsJsonObject();
            basicPropertyValuesChecker.check(basicProperty);

            doidBasicPropertyValueList.add(ImmutableDoidBasicPropertyValue.builder()
                    .pred(string(basicProperty, "pred"))
                    .val(string(basicProperty, "val"))
                    .build());
        }

        return doidBasicPropertyValueList;
    }

    @Nullable
    private static List<DoidLogicalDefinitionAxioms> extractDoidLogicalDefinitionAxioms(@Nullable JsonArray logicalDefinitionAxiomsArray) {
        //TODO check
        if (logicalDefinitionAxiomsArray == null) {
            return null;
        }

        List<DoidLogicalDefinitionAxioms> doidLogicalDefinitionAxioms = Lists.newArrayList();
        JsonDatamodelChecker doidLogicalDefinitionAxiomsChecker = DoidDatamodelCheckerFactory.doidLogicalDefinitionAxiomChecker();

        for (JsonElement logicalDefinitionAxioms : logicalDefinitionAxiomsArray) {
            JsonObject logicalDefinitionAxiomsObject = logicalDefinitionAxioms.getAsJsonObject();
            doidLogicalDefinitionAxiomsChecker.check(logicalDefinitionAxiomsObject);

            doidLogicalDefinitionAxioms.add(ImmutableDoidLogicalDefinitionAxioms.builder().build());
        }

        return doidLogicalDefinitionAxioms;
    }

    @VisibleForTesting
    @NotNull
    public static String extractDoid(@NotNull String url) {
        return url.replace("http://purl.obolibrary.org/obo/DOID_", "");
    }

    @Nullable
    private static DoidMetadata extractDoidMetadata(@Nullable JsonObject metadataObject) {
        //TODO check
        if (metadataObject == null) {
            return null;
        }

        JsonDatamodelChecker metadataChecker = DoidDatamodelCheckerFactory.doidMetadataChecker();
        metadataChecker.check(metadataObject);

        JsonArray xrefArray = metadataObject.getAsJsonArray("xrefs");
        List<DoidXref> xrefValList = Lists.newArrayList();
        if (xrefArray != null) {
            JsonDatamodelChecker xrefChecker = DoidDatamodelCheckerFactory.doidMetadataXrefChecker();

            for (JsonElement xref : xrefArray) {
                xrefChecker.check(xref.getAsJsonObject());
                xrefValList.add(ImmutableDoidXref.builder().val(string(xref.getAsJsonObject(), "val")).build());
            }
        }

        ImmutableDoidMetadata.Builder doidMetadataBuilder = ImmutableDoidMetadata.builder();
        doidMetadataBuilder.synonyms(extractDoidSynonyms(optionalJsonArray(metadataObject, "synonyms")));
        doidMetadataBuilder.basicPropertyValues(extractBasicPropertyValues(optionalJsonArray(metadataObject, "basicPropertyValues")));
        doidMetadataBuilder.doidDefinition(extractDoidDefinition(optionalJsonObject(metadataObject, "definition")));
        doidMetadataBuilder.subsets(optionalStringList(metadataObject, "subsets"));
        doidMetadataBuilder.xrefs(xrefValList);
        return doidMetadataBuilder.build();
    }

    @Nullable
    private static List<DoidSynonym> extractDoidSynonyms(@Nullable JsonArray synonymArray) {
        //TODO check
        if (synonymArray == null) {
            return null;
        }

        JsonDatamodelChecker doidSynonymsChecker = DoidDatamodelCheckerFactory.doidSynonymsChecker();

        List<DoidSynonym> doidSynonymList = Lists.newArrayList();
        for (JsonElement synonymElement : synonymArray) {
            JsonObject synonym = synonymElement.getAsJsonObject();
            doidSynonymsChecker.check(synonym);

            doidSynonymList.add(ImmutableDoidSynonym.builder()
                    .pred(string(synonym, "pred"))
                    .val(string(synonym, "val"))
                    .xrefs(stringList(synonym, "xrefs"))
                    .build());
        }

        return doidSynonymList;
    }

    @Nullable
    private static DoidDefinition extractDoidDefinition(@Nullable JsonObject definitionObject) {
        //TODO check
        if (definitionObject == null) {
            return null;
        }

        JsonDatamodelChecker doidDefinitionChecker = DoidDatamodelCheckerFactory.doidDefinitionChecker();
        doidDefinitionChecker.check(definitionObject);

        ImmutableDoidDefinition.Builder doidDefinitionBuilder = ImmutableDoidDefinition.builder();
        doidDefinitionBuilder.definitionVal(string(definitionObject, "val"));
        doidDefinitionBuilder.definitionXrefs(stringList(definitionObject, "xrefs"));
        return doidDefinitionBuilder.build();
    }
}
