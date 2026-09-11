package com.silver.aipets.fabric.authority;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.common.transport.AccountLinkWireResult;
import com.silver.aipets.common.transport.CustomerPortalWireResult;
import com.silver.aipets.common.transport.RecallResetWireResult;
import com.silver.aipets.common.transport.SubscriptionAccessWireResult;
import com.silver.aipets.common.transport.DialogueHistoryWireResult;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/** Async gameplay boundary implemented by the authenticated central pet-service client. */
public interface PetAuthorityGateway {
    default CompletionStage<AccountLinkWireResult> createAccountLink(UUID ownerUuid) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Account linking is not configured"));
    }

    default CompletionStage<CustomerPortalWireResult> createCustomerPortal(UUID ownerUuid) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Customer Portal is not configured"));
    }

    /** Returns the authoritative adoption entitlement for this UUID. */
    default CompletionStage<Boolean> findSubscriptionAccess(UUID ownerUuid) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Subscription access is not configured"));
    }

    /** Returns the bounded lifecycle projection used by the billing UI. */
    default CompletionStage<SubscriptionAccessWireResult> findSubscriptionDetails(UUID ownerUuid) {
        return findSubscriptionAccess(ownerUuid)
                .thenApply(SubscriptionAccessWireResult::new);
    }

    default CompletionStage<DialogueHistoryWireResult> findDialogueHistory(UUID ownerUuid) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Dialogue history is not configured"));
    }

    default CompletionStage<RecallResetWireResult> resetRecall(UUID petId) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Recall reset is not configured"));
    }

    CompletionStage<PetAdoptionWireResult> adopt(PetAdoptionWireRequest request);

    CompletionStage<Optional<PetAuthoritySnapshot>> findByOwner(UUID ownerUuid);

    CompletionStage<Optional<PetAuthoritySnapshot>> findByPetId(UUID petId);

    CompletionStage<AuthorityMutationResult> place(
            UUID operationId,
            UUID petId,
            PetTransitions.Place command);

    CompletionStage<AuthorityMutationResult> compensatePlaceFailure(
            UUID operationId,
            UUID petId,
            PetTransitions.CompensatePlaceFailure command);

    CompletionStage<AuthorityMutationResult> pickup(
            UUID operationId,
            UUID petId,
            PetTransitions.Pickup command);

    default CompletionStage<AuthorityMutationResult> prepareTransfer(
            UUID operationId,
            UUID petId,
            PetTransitions.PrepareTransfer command) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Transfer preparation is not configured"));
    }

    default CompletionStage<AuthorityMutationResult> completeTransfer(
            UUID operationId,
            UUID petId,
            PetTransitions.CompleteTransfer command) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Transfer completion is not configured"));
    }

    default CompletionStage<AuthorityMutationResult> expireTransfer(
            UUID operationId,
            UUID petId,
            PetTransitions.ExpireTransfer command) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Transfer expiry is not configured"));
    }

    default CompletionStage<AuthorityMutationResult> adminRecover(
            UUID operationId,
            UUID petId,
            PetTransitions.AdminRecover command) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Admin recovery is not configured"));
    }

    CompletionStage<PetRecallWireResult> recall(
            UUID operationId,
            UUID petId,
            PetTransitions.Recall command);

    CompletionStage<PetRecallWireResult> compensateRecallFailure(
            UUID recallOperationId,
            UUID petId,
            PetTransitions.CompensateRecallFailure command);
}
