package graphql.execution.nextgen.depgraph;

import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitorStub;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class DependencyGraph {


    public DependencyGraph createDependencyGraph(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {

        FieldCollectorWTC fieldCollector = new FieldCollectorWTC();
        FieldCollectorParameters parameters = FieldCollectorParameters
                .newParameters()
                .schema(graphQLSchema)
                .build();

        Function<MergedFieldWithType, List<MergedFieldWithType>> getChildren = mergedFieldWithType -> {
            List<MergedFieldWithType> childs = fieldCollector.collectFields(parameters, mergedFieldWithType);
            return childs;
        };


        Set<FieldVertex> allVertices = new LinkedHashSet<>();

        Traverser<MergedFieldWithType> traverser = Traverser.depthFirst(getChildren);
        FieldVertex rootVertex = new FieldVertex(null, null, null, null, null);
        traverser.rootVar(FieldVertex.class, rootVertex);
        allVertices.add(rootVertex);
        List<MergedFieldWithType> roots = fieldCollector.collectFromOperation(parameters, operationDefinition, graphQLSchema.getQueryType());
        traverser.traverse(roots, new TraverserVisitorStub<MergedFieldWithType>() {
            @Override
            public TraversalControl enter(TraverserContext<MergedFieldWithType> context) {
                MergedFieldWithType mergedFieldWithType = context.thisNode();
                System.out.println(mergedFieldWithType.getName() + "" + mergedFieldWithType.getObjectType().getName());

                FieldVertex fieldVertex = createFieldVertex(mergedFieldWithType, graphQLSchema);
                FieldVertex parentVertex = context.getVarFromParents(FieldVertex.class);
                fieldVertex.addDependency(parentVertex);
                parentVertex.addDependsOnMe(fieldVertex);
                allVertices.add(fieldVertex);
                context.setVar(FieldVertex.class, fieldVertex);

                return TraversalControl.CONTINUE;
            }

        });

        List<Set<FieldVertex>> listOfClosures = new ArrayList<>();
        Set<FieldVertex> allClosed = new LinkedHashSet<>();
        allClosed.add(rootVertex);
        Set<FieldVertex> curSourceSet = new LinkedHashSet<>();
        curSourceSet.add(rootVertex);

        while (!curSourceSet.isEmpty()) {
            Set<FieldVertex> nextClosure = new LinkedHashSet<>();
            allClosed.addAll(curSourceSet);
            for (FieldVertex source : curSourceSet) {
                for (FieldVertex dependsOnSource : source.getDependsOnMe()) {
                    if (allClosed.containsAll(dependsOnSource.getDependencies())) {
                        nextClosure.add(dependsOnSource);
                    }
                }
            }
            listOfClosures.add(nextClosure);
            curSourceSet = nextClosure;
        }

        System.out.println(listOfClosures);

        StringBuilder dotFile = new StringBuilder();
        dotFile.append("digraph G{\n");
        Traverser<FieldVertex> traverserVertex = Traverser.depthFirst(FieldVertex::getDependsOnMe);

        traverserVertex.traverse(rootVertex, new TraverserVisitorStub<FieldVertex>() {
            @Override
            public TraversalControl enter(TraverserContext<FieldVertex> context) {
                FieldVertex fieldVertex = context.thisNode();
                FieldVertex parentVertex = context.getParentNode();
                if (parentVertex != null) {
                    String vertexId = fieldVertex.getClass().getSimpleName() + Integer.toHexString(fieldVertex.hashCode());
                    String parentVertexId = parentVertex.getClass().getSimpleName() + Integer.toHexString(parentVertex.hashCode());
                    dotFile.append(vertexId).append(" -> ").append(parentVertexId).append(";\n");
                }
                allVertices.add(context.thisNode());
                return TraversalControl.CONTINUE;
            }
        });

        for (FieldVertex fieldVertex : allVertices) {
            dotFile.append(fieldVertex.getClass().getSimpleName()).append(Integer.toHexString(fieldVertex.hashCode())).append("[label=\"")
                    .append(fieldVertex.toString()).append("\"];\n");
        }

        dotFile.append("}");
        System.out.println(dotFile);
        return null;
    }

    private FieldVertex createFieldVertex(MergedFieldWithType mergedFieldWithType, GraphQLSchema graphQLSchemas) {
        return new FieldVertex(mergedFieldWithType.getFields(),
                mergedFieldWithType.getFieldDefinition(),
                mergedFieldWithType.getFieldsContainer(),
                mergedFieldWithType.getParentType(),
                mergedFieldWithType.getObjectType()
        );

    }


    public static void main(String[] args) {

    }
}
