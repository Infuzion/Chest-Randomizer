package infuzion.chest.randomizer.util.configuration;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentWrapper;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SerializableAs("ChestRandomizationItem")
public class chestRandomizationItem implements ConfigurationSerializable {
    private String configValue;
    private Material item;
    private short data = 0;
    private int percent = 0;
    private boolean error = false;
    private int amount = 1;
    private String lore = "";
    private ItemStack itemstack;
    private String itemName = "";
    private Map<Enchantment, Integer> enchantmentMap = new HashMap<Enchantment, Integer>();

    public chestRandomizationItem(ItemStack itemStack, int percent) {
        this.itemstack = itemStack;
        this.percent = percent;
        ItemMeta itemMeta = itemStack.getItemMeta();

        this.item = itemStack.getType();
        this.data = itemStack.getDurability();

        this.enchantmentMap = itemStack.getEnchantments();
        this.lore = "";

        if (itemMeta.hasLore()) {
            for (String e : itemMeta.getLore()) {
                lore += e.replaceAll("§", "&") + "||";
            }
        }

        setConfigValue();
    }

    private void setConfigValue() {
        generateItem();
        ItemMeta itemMeta = this.itemstack.getItemMeta();

        String loreToReturn = "";
        if (itemMeta.hasLore()) {
            for (String e : itemMeta.getLore()) {
                loreToReturn += e.replaceAll("§", "&") + "||";
            }
        }

        this.configValue = percent + "% " + item.toString() + ":" + data + " " + amount + " " + getEnchantmentsAsString() + " " + loreToReturn;
    }

    private String getEnchantmentsAsString() {
        String enchantments = "";

        for (Enchantment e : enchantmentMap.keySet()) {
            if (enchantments.equalsIgnoreCase("")) {
                enchantments += e.getName() + ":" + enchantmentMap.get(e);

            } else {
                enchantments += "," + e.getName() + ":" + enchantmentMap.get(e);
            }
        }

        if (enchantments.equalsIgnoreCase("")) {
            enchantments = "none";
        }
        return enchantments;
    }

    private void generateItem() {
        itemstack = new ItemStack(item, amount, data);
        itemstack.addUnsafeEnchantments(enchantmentMap);
        ItemMeta itemMeta = itemstack.getItemMeta();
        String[] loreArray = lore.split("\\|\\|");
        List<String> loreList = new ArrayList<String>();
        for (String e : loreArray) {
            loreList.add(ChatColor.translateAlternateColorCodes('&', e).trim());
        }
        itemMeta.setLore(loreList);
        itemMeta.setDisplayName(itemName);
        itemstack.setItemMeta(itemMeta);
    }

    @SuppressWarnings("WeakerAccess")
    public chestRandomizationItem(Map data) {
        this.percent = (Integer) data.get("percent");
        this.itemName = (String) data.get("name");
        this.lore = (String) data.get("lore");
        this.data = Short.parseShort(String.valueOf(data.get("data")));
        this.item = Material.valueOf(String.valueOf(data.get("material")));
        this.amount = (Integer) data.get("amount");
        addEnchantments((String) data.get("enchantments"));
        generateItem();
    }

    private void addEnchantments(String enchantments) {
        if (enchantments.equalsIgnoreCase("none")) {
            return;
        }

        int enchantmentLevel;
        Enchantment enchantment;
        int enchantmentID;

        String[] enchantmentSplit = enchantments.split(",", 0); // enchant1,enchant2 -> [enchantname],[lvl]
        for (String e : enchantmentSplit) {
            try {
                if (e.split(":", 2).length != 2) {
                    throw new ArrayIndexOutOfBoundsException();
                }

                enchantment = Enchantment.getByName(e.split(":", 2)[0].toUpperCase());
                enchantmentLevel = Integer.parseInt(e.split(":", 2)[1]);

                if (enchantment == null) {
                    enchantmentID = Integer.parseInt(e.split(":", 2)[0]);
                    enchantment = new EnchantmentWrapper(enchantmentID);
                }
                enchantmentMap.put(enchantment, enchantmentLevel);
            } catch (Exception err) {
                Bukkit.getLogger().severe("Failed to read item enchant in config: " + e);
                return;
            }

        }
    }

    chestRandomizationItem(String configValue) {
        this(configValue, false);
    }

    chestRandomizationItem(String configValue, boolean old) {
        String[] split = configValue.trim().split(" ", 5);
        if (split.length < 2) {
            Bukkit.getLogger().severe("Failed to read config line: " + configValue);
        }

        //split[0] = percent
        //split[1] = name:data
        //split[2] = amount
        //split[3] = enchants
        //split[4] = lore
        String sPercent = split[0].trim();
        String sNameData = split[1].trim();
        String sAmount = "1";
        String sEnchants = "none";
        String sLore = "";
        if (split.length > 2) {
            sAmount = split[2].trim();
        }
        if (split.length > 3) {
            sEnchants = split[3].trim();
        }
        if (split.length > 4) {
            sLore = split[4].trim();
        }
        if (old) {
            sAmount = "1";
            if (split.length > 3) {
                sEnchants = split[2].trim();
            }
            if (split.length > 4) {
                sLore = split[3].trim();
            }
        }

        String sData;
        String sName;

        if (sNameData.contains(":")) {
            String[] saNameData = sNameData.split(":"); //[name]:[data]
            sData = saNameData[1];
            sName = saNameData[0];
        } else {
            sData = "0";
            sName = sNameData;
        }

        try {
            percent = Integer.parseInt(sPercent.replace("%", "")); //[percent]
        } catch (NumberFormatException e) {
            Bukkit.getLogger().severe("Failed to read percent in config: " + sPercent);
            error = true;
            return;
        }

        try {
            data = Short.parseShort(sData); //[data]
        } catch (NumberFormatException e) {
            Bukkit.getLogger().severe("Failed to read item data in config: " + sData);
            error = true;
            return;
        }

        item = Material.matchMaterial(sName); //[name]
        if (item == null) {
            Bukkit.getLogger().severe("Failed to read item name in config: " + sName);
            error = true;
            return;
        }

        addAmount(sAmount);
        addEnchantments(sEnchants);
        addLore(sLore);

        generateItem();
        setConfigValue();
    }

    private void addAmount(String amount) {
        try {
            this.amount = Integer.parseInt(amount);
        } catch (Exception e) {
            this.amount = 1;
        }
    }

    private void addLore(String lore) {
        this.lore = lore;
    }

    @SuppressWarnings("unused")
    public static chestRandomizationItem deserialize(Map data) {
        return new chestRandomizationItem(data);
    }

    public ItemStack getItem() {
        return itemstack;
    }

    public int getPercent() {
        return percent;
    }

    boolean hasError() {
        return error;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<String, Object>();
        generateItem();
        map.put("name", itemName);
        map.put("material", item.toString());
        map.put("data", data);
        map.put("lore", lore);
        map.put("amount", amount);

        map.put("enchantments", getEnchantmentsAsString());
        map.put("percent", percent);
        return map;
    }

    @Override
    public String toString() {
        return configValue;
    }
}
