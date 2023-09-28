package pl.asie.computronics.integration.railcraft.signalling;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mods.railcraft.api.core.WorldCoordinate;
import mods.railcraft.api.signals.SignalAspect;
import mods.railcraft.api.signals.SignalController;
import mods.railcraft.api.signals.SignalReceiver;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;
import pl.asie.computronics.util.collect.SimpleInvertibleDualMap;

import javax.annotation.Nonnull;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Vexatos
 */
public class MassiveSignalReceiver extends SignalReceiver {

	private final Map<WorldCoordinate, SignalAspect> aspects = new HashMap<WorldCoordinate, SignalAspect>();
	private final SimpleInvertibleDualMap<String, WorldCoordinate> signalNames = SimpleInvertibleDualMap.create();
	private SignalAspect visualAspect = SignalAspect.BLINK_RED;
	private SignalAspect mostRestrictive;

	public MassiveSignalReceiver(String locTag, TileEntity tile) {
		super(locTag, tile, 128);
	}

	public SignalAspect getAspectFor(WorldCoordinate coord) {
		return this.aspects.get(coord);
	}

	public SignalAspect getVisualAspect() {
		return this.visualAspect != null ? this.visualAspect : SignalAspect.BLINK_RED;
	}

	public void setVisualAspect(SignalAspect aspect) {
		this.visualAspect = aspect;
	}

	public String getNameFor(WorldCoordinate coord) {
		return this.signalNames.inverse().get(coord);
	}

	public Collection<WorldCoordinate> getCoordsFor(String name) {
		return this.signalNames.get(name);
	}

	public SignalAspect getMostRestrictiveAspectFor(String name) {
		SignalAspect mostRestrictive = null;
		for(WorldCoordinate coord : this.signalNames.get(name)) {
			if(mostRestrictive == null) {
				mostRestrictive = this.aspects.get(coord);
			} else {
				mostRestrictive = SignalAspect.mostRestrictive(mostRestrictive, this.aspects.get(coord));
			}
		}
		return mostRestrictive;
	}

	public String getNameFor(SignalController con) {
		String name = this.signalNames.inverse().get(con.getCoords());
		if(name == null) {
			name = con.getName();
			if(name != null) {
				this.signalNames.put(name, con.getCoords());
			}
		}
		return name;
	}

	public SignalAspect getMostRestrictiveAspect() {
		if(this.mostRestrictive != null) {
			return this.mostRestrictive;
		}
		SignalAspect mostRestrictive = null;
		for(SignalAspect aspect : this.aspects.values()) {
			if(mostRestrictive == null) {
				mostRestrictive = aspect;
			} else {
				mostRestrictive = SignalAspect.mostRestrictive(mostRestrictive, aspect);
			}
		}
		return this.mostRestrictive = mostRestrictive != null ? mostRestrictive : SignalAspect.GREEN;
	}

	public Set<String> getSignalNames() {
		return this.signalNames.keySet();
	}

	@Override
	public void onControllerAspectChange(SignalController con, @Nonnull SignalAspect aspect) {
		WorldCoordinate coords = con.getCoords();
		SignalAspect oldAspect = this.aspects.get(coords);
		if(oldAspect != aspect) {
			this.aspects.put(coords, aspect);
			this.mostRestrictive = null;
			super.onControllerAspectChange(con, aspect);
		}
		String name = con.getName();
		if(name != null && !signalNames.containsEntry(name, coords)) {
			signalNames.put(name, coords);
		}
	}

	@Override
	public void onPairNameChange(WorldCoordinate coords, String name) {
		super.onPairNameChange(coords, name);
		if(name != null) {
			this.signalNames.put(name, coords);
		} else {
			this.signalNames.removeValue(coords);
		}
	}

	@Override
	public void cleanPairings() {
		super.cleanPairings();
		Collection<WorldCoordinate> pairs = getPairs();
		if(this.aspects.keySet().retainAll(pairs)) {
			this.mostRestrictive = null;
		}
		this.signalNames.retainAllValues(pairs);
	}

	@Override
	public void clearPairings() {
		super.clearPairings();
		this.aspects.clear();
		this.mostRestrictive = null;
		this.signalNames.clear();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void removePair(int x, int y, int z) {
		super.removePair(x, y, z);
		Collection<WorldCoordinate> pairs = getPairs();
		if(this.aspects.keySet().retainAll(pairs)) {
			this.mostRestrictive = null;
		}
		this.signalNames.retainAllValues(pairs);
	}

	@Override
	protected void saveNBT(NBTTagCompound data) {
		super.saveNBT(data);
		NBTTagList list = new NBTTagList();

		for(Map.Entry<WorldCoordinate, SignalAspect> entry : this.aspects.entrySet()) {
			NBTTagCompound tag = new NBTTagCompound();
			WorldCoordinate key = entry.getKey();
			tag.setIntArray("coords", new int[] { key.dimension, key.x, key.y, key.z });
			tag.setByte("aspect", (byte) entry.getValue().ordinal());
			String s = signalNames.inverse().get(key);
			if(s != null) {
				tag.setString("name", s);
			}
			list.appendTag(tag);
		}
		data.setTag("aspects", list);
	}

	@Override
	protected void loadNBT(NBTTagCompound data) {
		super.loadNBT(data);
		NBTTagList list = data.getTagList("aspects", Constants.NBT.TAG_COMPOUND);

		for(byte entry = 0; entry < list.tagCount(); ++entry) {
			NBTTagCompound tag = list.getCompoundTagAt(entry);
			int[] c = tag.getIntArray("coords");
			WorldCoordinate coord = new WorldCoordinate(c[0], c[1], c[2], c[3]);
			this.aspects.put(coord, SignalAspect.fromOrdinal(tag.getByte("aspect")));
			if(tag.hasKey("name")) {
				signalNames.put(tag.getString("name"), coord);
			}
		}
		this.mostRestrictive = null;
	}

	@Override
	public void writePacketData(DataOutputStream data) throws IOException {
		super.writePacketData(data);
		data.writeByte(this.visualAspect != null ? this.visualAspect.ordinal() : SignalAspect.BLINK_RED.ordinal());
	}

	@Override
	public void readPacketData(DataInputStream data) throws IOException {
		super.readPacketData(data);
		this.visualAspect = SignalAspect.fromOrdinal(data.readByte());
	}
}
