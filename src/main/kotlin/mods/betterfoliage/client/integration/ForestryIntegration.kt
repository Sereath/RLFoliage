package mods.betterfoliage.client.integration

import mods.betterfoliage.BetterFoliageMod
import mods.betterfoliage.client.Client
import mods.betterfoliage.client.texture.LeafInfo
import mods.betterfoliage.client.texture.LeafRegistry
import mods.betterfoliage.client.texture.StandardLeafKey
import mods.betterfoliage.loader.Refs
import mods.octarinecore.client.resource.ModelRenderKey
import mods.octarinecore.client.resource.ModelRenderRegistry
import mods.octarinecore.getTileEntitySafe
import mods.octarinecore.metaprog.ClassRef
import mods.octarinecore.metaprog.FieldRef
import mods.octarinecore.metaprog.MethodRef
import mods.octarinecore.metaprog.allAvailable
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.apache.logging.log4j.Level
import kotlin.collections.Map
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.emptyMap
import kotlin.collections.find
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.listOf
import kotlin.collections.mapValues
import kotlin.collections.mutableMapOf
import kotlin.collections.set

@SideOnly(Side.CLIENT)
object ForestryIntegration {

    val TextureLeaves = ClassRef("forestry.arboriculture.models.TextureLeaves")
    val TeLleafTextures = FieldRef(TextureLeaves, "leafTextures", Refs.Map)
    val TeLplain = FieldRef(TextureLeaves, "plain", Refs.ResourceLocation)
    val TeLfancy = FieldRef(TextureLeaves, "fancy", Refs.ResourceLocation)
    val TeLpollplain = FieldRef(TextureLeaves, "pollinatedPlain", Refs.ResourceLocation)
    val TeLpollfancy = FieldRef(TextureLeaves, "pollinatedFancy", Refs.ResourceLocation)
    val TileLeaves = ClassRef("forestry.arboriculture.tiles.TileLeaves")
    val TiLgetLeaveSprite = MethodRef(TileLeaves, "getLeaveSprite", Refs.ResourceLocation, ClassRef.boolean)

    val PropertyTreeType = ClassRef("forestry.arboriculture.blocks.PropertyTreeType")
    val TreeDefinition = ClassRef("forestry.arboriculture.genetics.TreeDefinition")
    val IAlleleTreeSpecies = ClassRef("forestry.api.arboriculture.IAlleleTreeSpecies")
    val ILeafSpriteProvider = ClassRef("forestry.api.arboriculture.ILeafSpriteProvider")
    val TdSpecies = FieldRef(TreeDefinition, "species", IAlleleTreeSpecies)
    val getLeafSpriteProvider = MethodRef(IAlleleTreeSpecies, "getLeafSpriteProvider", ILeafSpriteProvider)
    val getSprite = MethodRef(ILeafSpriteProvider, "getSprite", Refs.ResourceLocation, ClassRef.boolean, ClassRef.boolean)

    init {
        if (Loader.isModLoaded("forestry") && allAvailable(TiLgetLeaveSprite, getLeafSpriteProvider, getSprite)) {
            Client.log(Level.INFO, "Forestry support initialized")
            LeafRegistry.addRegistry(ForestryLeafRegistry)
        }
    }
}

object ForestryLeafRegistry : ModelRenderRegistry<LeafInfo> {
    val logger = BetterFoliageMod.logDetail
    val textureToKey = mutableMapOf<ResourceLocation, ModelRenderKey<LeafInfo>>()
    var textureToValue = emptyMap<ResourceLocation, LeafInfo>()

    override fun get(state: IBlockState, world: IBlockAccess, pos: BlockPos): LeafInfo? {
        // check variant property (used in decorative leaves)
        state.properties.entries.find {
            ForestryIntegration.PropertyTreeType.isInstance(it.key) && ForestryIntegration.TreeDefinition.isInstance(it.value)
        } ?.let {
            val species = ForestryIntegration.TdSpecies.get(it.value)
            val spriteProvider = ForestryIntegration.getLeafSpriteProvider.invoke(species!!)
            val textureLoc = ForestryIntegration.getSprite.invoke(spriteProvider!!, false, Minecraft.isFancyGraphicsEnabled())
            return textureToValue[textureLoc]
        }

        // extract leaf texture information from TileEntity
        val tile = world.getTileEntitySafe(pos) ?: return null
        if (!ForestryIntegration.TileLeaves.isInstance(tile)) return null
        val textureLoc = ForestryIntegration.TiLgetLeaveSprite.invoke(tile, Minecraft.isFancyGraphicsEnabled()) ?: return null
        return textureToValue[textureLoc]
    }

    @SubscribeEvent
    fun handlePreStitch(event: TextureStitchEvent.Pre) {
        textureToValue = emptyMap()

        val allLeaves = ForestryIntegration.TeLleafTextures.getStatic() as Map<*, *>
        allLeaves.entries.forEach {
            logger.log(Level.DEBUG, "ForestryLeavesSupport: base leaf type ${it.key.toString()}")
            listOf(
                ForestryIntegration.TeLplain.get(it.value) as ResourceLocation,
                ForestryIntegration.TeLfancy.get(it.value) as ResourceLocation,
                ForestryIntegration.TeLpollplain.get(it.value) as ResourceLocation,
                ForestryIntegration.TeLpollfancy.get(it.value) as ResourceLocation
            ).forEach { textureLocation ->
                val key = StandardLeafKey(logger, textureLocation.toString()).apply { onPreStitch(event.map) }
                textureToKey[textureLocation] = key
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun handlePostStitch(event: TextureStitchEvent.Post) {
        textureToValue = textureToKey.mapValues { (_, key) -> key.resolveSprites(event.map) }
        textureToKey.clear()
    }
}