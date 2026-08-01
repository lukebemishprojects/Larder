package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record DeploymentPackage(Identifier<Deployment> source, Package.ModuleComponentIdentifier value) implements Model.OneToMany<Deployment, Package.ModuleComponentIdentifier> {
    public static final Partial<DeploymentPackage, ByDeployment> BY_DEPLOYMENT = new Partial<>("by_deployment");
    public record ByDeployment(Identifier<Deployment> deployment) implements Partial.Value<DeploymentPackage, ByDeployment> {
        @Override
        public Partial<DeploymentPackage, ByDeployment> type() {
            return BY_DEPLOYMENT;
        }
    }

    public static final Representation<DeploymentPackage> REPRESENTATION = Representation.build(
        Representation.referenceField("deployment", () -> Deployment.REPRESENTATION, DeploymentPackage::source),
        Package.ModuleComponentIdentifier.representation(DeploymentPackage::value),
        (it, source, value) -> {
            it.partial(BY_DEPLOYMENT, source, ByDeployment::deployment);
            return it.build("deploymentpackages", result -> new DeploymentPackage(
                source.get(result),
                value.get(result)
            ));
        }
    );
}
