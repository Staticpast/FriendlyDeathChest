package io.mckenz.friendlydeathchest.model;

import java.util.UUID;

/**
 * Represents data for a death chest
 */
public class ChestData {
    private final UUID ownerUUID;
    private final long creationTime;
    private final int experience;
    private long expiryTime;
    private boolean warningShown;
    
    /**
     * Creates new chest data
     * 
     * @param ownerUUID The UUID of the player who died
     * @param experience The amount of experience stored in the chest
     * @param expiryTime The time when the chest will expire (0 for no expiry)
     */
    public ChestData(UUID ownerUUID, int experience, long expiryTime) {
        this.ownerUUID = ownerUUID;
        this.experience = experience;
        this.expiryTime = expiryTime;
        this.creationTime = System.currentTimeMillis();
        this.warningShown = false;
    }
    
    /**
     * Gets the UUID of the player who died
     * 
     * @return The UUID of the player who died
     */
    public UUID getOwnerUUID() {
        return ownerUUID;
    }
    
    /**
     * Gets the amount of experience stored in the chest
     * 
     * @return The amount of experience
     */
    public int getExperience() {
        return experience;
    }
    
    /**
     * Gets the time when the chest will expire
     * 
     * @return The expiry time in milliseconds (0 for no expiry)
     */
    public long getExpiryTime() {
        return expiryTime;
    }
    
    /**
     * Sets the expiry time for the chest
     * 
     * @param expiryTime The new expiry time
     */
    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }
    
    /**
     * Gets the time when the chest was created
     * 
     * @return The creation time in milliseconds
     */
    public long getCreationTime() {
        return creationTime;
    }
    
    /**
     * Checks if an expiry warning has been shown
     * 
     * @return true if a warning has been shown
     */
    public boolean isWarningShown() {
        return warningShown;
    }
    
    /**
     * Sets whether an expiry warning has been shown
     * 
     * @param warningShown true if a warning has been shown
     */
    public void setWarningShown(boolean warningShown) {
        this.warningShown = warningShown;
    }
} 