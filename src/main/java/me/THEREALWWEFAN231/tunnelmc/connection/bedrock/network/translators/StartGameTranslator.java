package me.THEREALWWEFAN231.tunnelmc.connection.bedrock.network.translators;

import com.nukkitx.protocol.bedrock.data.GameRuleData;
import com.nukkitx.protocol.bedrock.packet.RequestChunkRadiusPacket;
import com.nukkitx.protocol.bedrock.packet.StartGamePacket;
import com.nukkitx.protocol.bedrock.packet.TickSyncPacket;
import me.THEREALWWEFAN231.tunnelmc.TunnelMC;
import me.THEREALWWEFAN231.tunnelmc.connection.PacketIdentifier;
import me.THEREALWWEFAN231.tunnelmc.connection.PacketTranslator;
import me.THEREALWWEFAN231.tunnelmc.connection.bedrock.Client;
import me.THEREALWWEFAN231.tunnelmc.translator.dimension.Dimension;
import me.THEREALWWEFAN231.tunnelmc.translator.gamemode.GameModeTranslator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@PacketIdentifier(StartGamePacket.class)
public class StartGameTranslator extends PacketTranslator<StartGamePacket> {

	@Override
	public void translate(StartGamePacket packet, Client client) {
		for (StartGamePacket.ItemEntry itemEntry : packet.getItemEntries()) {
			if (itemEntry.getIdentifier().equals("minecraft:shield")) {
				client.bedrockClient.getSession().getHardcodedBlockingId().set(itemEntry.getId());
				break;
			}
		}

		client.entityRuntimeId = (int) packet.getRuntimeEntityId();
		client.defaultGameMode = packet.getLevelGameType();

		GameMode gameMode = GameModeTranslator.bedrockToJava(packet.getPlayerGameType());

		Dimension dimension = Dimension.getDimensionFromId(packet.getDimensionId()).orElse(Dimension.OVERWORLD);
		RegistryKey<DimensionType> dimensionRegistryKey = dimension.getDimensionRegistryKey();
		RegistryKey<World> worldRegistryKey = dimension.getWorldRegistryKey();

		long seed = packet.getSeed();
		int maxPlayers = 999;
		int chunkLoadDistance = 3;
		boolean showDeathScreen = true;

		for (GameRuleData<?> gameRule : packet.getGamerules()) {
			if (gameRule.getName().equals("doimmediaterespawn")) {
				showDeathScreen = !((Boolean) gameRule.getValue());
				break;
			}
		}

		Set<RegistryKey<World>> dimensionIds = Stream.of(World.OVERWORLD, World.NETHER, World.END).collect(Collectors.toSet());

		GameJoinS2CPacket gameJoinS2CPacket = new GameJoinS2CPacket(client.entityRuntimeId, false, gameMode, gameMode, dimensionIds, DynamicRegistryManager.BUILTIN.get(), dimensionRegistryKey, worldRegistryKey, seed, maxPlayers, chunkLoadDistance, chunkLoadDistance, false, showDeathScreen, false, false, Optional.empty());
		client.javaConnection.processServerToClientPacket(gameJoinS2CPacket);
		
		client.onPlayerInitialized();

		// TODO: Send a complete SynchronizeTagsS2CPacket so that water can work.

		MinecraftClient.getInstance().execute(() -> GameRulesChangedTranslator.onGameRulesChanged(packet.getGamerules()));

		float x = packet.getPlayerPosition().getX();
		float y = packet.getPlayerPosition().getY();
		float z = packet.getPlayerPosition().getZ();
		float yaw = packet.getRotation().getX();
		float pitch = packet.getRotation().getY();

		PlayerPositionLookS2CPacket playerPositionLookS2CPacket = new PlayerPositionLookS2CPacket(x, y, z, yaw, pitch, Collections.emptySet(), 0, false);
//		client.javaConnection.processServerToClientPacket(playerPositionLookS2CPacket);

		int chunkX = MathHelper.floor(x) >> 4;
		int chunkZ = MathHelper.floor(z) >> 4;

		ChunkRenderDistanceCenterS2CPacket chunkRenderDistanceCenterS2CPacket = new ChunkRenderDistanceCenterS2CPacket(chunkX, chunkZ);
		client.javaConnection.processServerToClientPacket(chunkRenderDistanceCenterS2CPacket);

		RequestChunkRadiusPacket requestChunkRadiusPacket = new RequestChunkRadiusPacket();
		requestChunkRadiusPacket.setRadius(TunnelMC.mc.options.getViewDistance().getValue());
		client.sendPacketImmediately(requestChunkRadiusPacket);

		client.sendPacketImmediately(new TickSyncPacket());

		client.movementMode = packet.getPlayerMovementSettings().getMovementMode();
	}
}