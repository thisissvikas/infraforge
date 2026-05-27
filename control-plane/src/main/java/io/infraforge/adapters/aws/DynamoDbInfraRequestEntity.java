package io.infraforge.adapters.aws;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * DynamoDB Enhanced Client bean for {@code infraforge-requests}.
 *
 * <p>Table design:
 * <pre>
 *   PK:  requestId  (String)
 *   GSI: userId-createdAt-index
 *     GSI PK:   userId    (String)
 *     GSI SK:   createdAt (String — ISO-8601, lexicographically sortable)
 * </pre>
 * {@code stateType} stores the enum name (e.g. "PR_CREATED").
 * {@code stateMetadata} stores a JSON blob with state-specific fields so that
 * the sealed hierarchy is preserved without separate tables.</p>
 */
@DynamoDbBean
public class DynamoDbInfraRequestEntity {

    private String requestId;
    private String userId;
    private String userEmail;
    private String teamId;
    private String targetCloud;        // CloudProvider name e.g. "AWS", "GCP", "AZURE"
    private String stateType;
    private String stateMetadata;    // JSON; shape depends on stateType
    private String rawIntent;
    private String generatedTerraform;
    private String githubPrUrl;
    private String githubBranch;
    private String errorMessage;
    private double estimatedMonthlyCostUsd;
    private String createdAt;        // ISO-8601
    private String updatedAt;        // ISO-8601

    @DynamoDbPartitionKey
    public String getRequestId()                  { return requestId; }
    public void   setRequestId(String v)          { this.requestId = v; }

    @DynamoDbSecondaryPartitionKey(indexNames = "userId-createdAt-index")
    public String getUserId()                     { return userId; }
    public void   setUserId(String v)             { this.userId = v; }

    @DynamoDbSecondarySortKey(indexNames = "userId-createdAt-index")
    public String getCreatedAt()                  { return createdAt; }
    public void   setCreatedAt(String v)          { this.createdAt = v; }

    public String getUserEmail()                  { return userEmail; }
    public void   setUserEmail(String v)          { this.userEmail = v; }

    public String getTeamId()                     { return teamId; }
    public void   setTeamId(String v)             { this.teamId = v; }

    public String getTargetCloud()                { return targetCloud; }
    public void   setTargetCloud(String v)        { this.targetCloud = v; }

    public String getStateType()                  { return stateType; }
    public void   setStateType(String v)          { this.stateType = v; }

    public String getStateMetadata()              { return stateMetadata; }
    public void   setStateMetadata(String v)      { this.stateMetadata = v; }

    public String getRawIntent()                  { return rawIntent; }
    public void   setRawIntent(String v)          { this.rawIntent = v; }

    public String getGeneratedTerraform()         { return generatedTerraform; }
    public void   setGeneratedTerraform(String v) { this.generatedTerraform = v; }

    public String getGithubPrUrl()                { return githubPrUrl; }
    public void   setGithubPrUrl(String v)        { this.githubPrUrl = v; }

    public String getGithubBranch()               { return githubBranch; }
    public void   setGithubBranch(String v)       { this.githubBranch = v; }

    public String getErrorMessage()               { return errorMessage; }
    public void   setErrorMessage(String v)       { this.errorMessage = v; }

    public double getEstimatedMonthlyCostUsd()              { return estimatedMonthlyCostUsd; }
    public void   setEstimatedMonthlyCostUsd(double v)      { this.estimatedMonthlyCostUsd = v; }

    public String getUpdatedAt()                  { return updatedAt; }
    public void   setUpdatedAt(String v)          { this.updatedAt = v; }
}
