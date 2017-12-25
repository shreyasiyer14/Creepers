/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package system;

import com.google.common.collect.Lists;
import org.terasology.audio.StaticSound;
import org.terasology.audio.events.PlaySoundEvent;
import org.terasology.behaviors.components.FollowComponent;
import org.terasology.creepers.component.CreeperComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.health.DoDamageEvent;
import org.terasology.logic.health.OnDamagedEvent;
import org.terasology.logic.actions.ExplosionActionComponent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.utilities.Assets;
import org.terasology.utilities.random.FastRandom;
import org.terasology.utilities.random.Random;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;

import java.util.List;
import java.util.Optional;

@RegisterSystem(RegisterMode.AUTHORITY)
public class CreeperExplosionEvent extends BaseComponentSystem implements UpdateSubscriberSystem {
    @In
    private WorldProvider worldProvider;

    @In
    private BlockEntityRegistry blockEntityRegistry;

    @In
    private EntityManager entityManager;

    @In
    private BlockManager blockManager;

    private Random random = new FastRandom();
    private List<Optional<StaticSound>> explosionSounds = Lists.newArrayList();

    @Override
    public void initialise() {
        explosionSounds.add(Assets.getSound("core:explode1"));
        explosionSounds.add(Assets.getSound("core:explode2"));
        explosionSounds.add(Assets.getSound("core:explode3"));
        explosionSounds.add(Assets.getSound("core:explode4"));
        explosionSounds.add(Assets.getSound("core:explode5"));
    }

    @Override
    public void update (float delta) {
        for (EntityRef entity : entityManager.getEntitiesWith(FollowComponent.class, ExplosionActionComponent.class, CreeperComponent.class)) {
            CreeperComponent component = entity.getComponent(CreeperComponent.class);
            FollowComponent followComponent = entity.getComponent(FollowComponent.class);
            ExplosionActionComponent explosionComp = entity.getComponent(ExplosionActionComponent.class);

            Vector3f entityFollowingLocation = followComponent.entityToFollow.getComponent(LocationComponent.class).getWorldPosition();
            Vector3f currentActorLocation = entity.getComponent(LocationComponent.class).getWorldPosition();
            float maxDistance =  entity.getComponent(CreeperComponent.class).maxDistanceTillExplode;

            if (currentActorLocation.distanceSquared(entityFollowingLocation) <= maxDistance * maxDistance) {
                doExplosion(explosionComp, currentActorLocation, EntityRef.NULL, entity);
                followComponent.entityToFollow.send(new DoDamageEvent(explosionComp.damageAmount));
            }
        }
    }

    @ReceiveEvent
    public void onDamage(OnDamagedEvent event, EntityRef entity) {
        return;
    }

    private StaticSound getRandomExplosionSound() {
        return explosionSounds.get(random.nextInt(0, explosionSounds.size() - 1)).get();
    }

    void doExplosion(ExplosionActionComponent explosionComp, Vector3f origin, EntityRef instigatingBlockEntity, EntityRef currentActor) {
        EntityBuilder builder = entityManager.newBuilder("core:smokeExplosion");
        builder.getComponent(LocationComponent.class).setWorldPosition(origin);
        EntityRef smokeEntity = builder.build();

        smokeEntity.send(new PlaySoundEvent(getRandomExplosionSound(), 1f));

        Vector3i blockPos = new Vector3i();
        for (int i = 0; i < explosionComp.maxRange; i++) {
            Vector3f direction = random.nextVector3f(1.0f);

            for (int j = 0; j < 4; j++) {
                Vector3f target = new Vector3f(origin);

                target.x += direction.x * j;
                target.y += direction.y * j;
                target.z += direction.z * j;
                blockPos.set((int) target.x, (int) target.y, (int) target.z);
                Block currentBlock = worldProvider.getBlock(blockPos);

                /* PHYSICS */
                if (currentBlock.isDestructible()) {
                    EntityRef blockEntity = blockEntityRegistry.getEntityAt(blockPos);
                    // allow explosions to chain together,  but do not chain on the instigating block
                    if (!blockEntity.equals(instigatingBlockEntity) && blockEntity.hasComponent(ExplosionActionComponent.class)) {
                        doExplosion(blockEntity.getComponent(ExplosionActionComponent.class), blockPos.toVector3f(), blockEntity, currentActor);
                    } else {
                        blockEntity.send(new DoDamageEvent(explosionComp.damageAmount, explosionComp.damageType));
                    }
                }
            }
        }
        currentActor.destroy();
    }
}
