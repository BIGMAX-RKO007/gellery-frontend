package com.google.ai.edge.gallery.modeldownload

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelArtifactTest {
  @Test
  fun buildsHuggingFaceUrl() {
    val artifact =
      ModelArtifact.fromHuggingFace(
        id = "gemma",
        displayName = "Gemma",
        repositoryId = "google/gemma-test",
        fileName = "model.task",
        revision = "abc123",
      )

    assertEquals(
      "https://huggingface.co/google/gemma-test/resolve/abc123/model.task?download=true",
      artifact.url,
    )
  }

  @Test
  fun sanitizesStorageSegment() {
    assertEquals("google_gemma_4", safeSegment("google/gemma:4"))
  }
}
