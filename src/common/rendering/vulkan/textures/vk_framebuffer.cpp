/*
** vk_framebuffer.cpp
**
** Vulkan backend
**
**---------------------------------------------------------------------------
**
** Copyright 2017-2025 GZDoom Maintainers and Contributors
** Copyright 2025-2026 UZDoom Maintainers and Contributors
**
** SPDX-License-Identifier: GPL-3.0-or-later
**
**---------------------------------------------------------------------------
**
** Copyright 2016-2020 Magnus Norddahl
**
** SPDX-License-Identifier: Zlib
**
**---------------------------------------------------------------------------
**
*/

#include <zvulkan/vulkanobjects.h>
#include <zvulkan/vulkandevice.h>
#include <zvulkan/vulkanbuilders.h>
#include <zvulkan/vulkanswapchain.h>
#include "vulkan/system/vk_renderdevice.h"
#include "vulkan/renderer/vk_postprocess.h"
#include "vk_framebuffer.h"

#ifdef __ANDROID__
#include <zvulkan/vulkaninstance.h>
#include <zvulkan/vulkansurface.h>
bool I_CreateVulkanSurface(VkInstance instance, VkSurfaceKHR *surface);
bool I_AndroidTakeVulkanSurfaceReset();
void I_AndroidVulkanStarted();
#endif

CVAR(Bool, vk_hdr, false, CVAR_ARCHIVE | CVAR_GLOBALCONFIG);
CVAR(Bool, vk_exclusivefullscreen, false, CVAR_ARCHIVE | CVAR_GLOBALCONFIG);

VkFramebufferManager::VkFramebufferManager(VulkanRenderDevice* fb) : fb(fb)
{
	SwapChain = VulkanSwapChainBuilder()
		.Create(fb->device.get());

	SwapChainImageAvailableSemaphore = SemaphoreBuilder()
		.DebugName("SwapChainImageAvailableSemaphore")
		.Create(fb->device.get());
}

VkFramebufferManager::~VkFramebufferManager()
{
}

#ifdef __ANDROID__
// Android destroys the window surface while the app is in the background, so
// the old VkSurfaceKHR is dead when the app returns. Replace it with one for
// the new window, and rebuild the swapchain on it.
void VkFramebufferManager::RecreateAndroidSurface()
{
	VulkanDevice *device = fb->device.get();
	vkDeviceWaitIdle(device->device);

	Framebuffers.clear();
	RenderFinishedSemaphores.clear();
	SwapChain.reset(); // the swapchain must go before the surface it was made for

	auto surface = device->Surface;
	VkSurfaceKHR newSurface = VK_NULL_HANDLE;
	if (!I_CreateVulkanSurface(surface->Instance->Instance, &newSurface))
		VulkanError("Could not recreate the Vulkan surface after resuming");
	vkDestroySurfaceKHR(surface->Instance->Instance, surface->Surface, nullptr);
	surface->Surface = newSurface;

	SwapChain = VulkanSwapChainBuilder().Create(device); // starts out lost: rebuilt below
}
#endif

void VkFramebufferManager::AcquireImage()
{
#ifdef __ANDROID__
	if (I_AndroidTakeVulkanSurfaceReset())
		RecreateAndroidSurface();
#endif

	bool exclusiveFullscreen = fb->IsFullscreen() && vk_exclusivefullscreen;
	if (SwapChain->Lost() || fb->GetClientWidth() != CurrentWidth || fb->GetClientHeight() != CurrentHeight || fb->GetVSync() != CurrentVSync || CurrentHdr != vk_hdr || CurrentExclusiveFullscreen != exclusiveFullscreen)
	{
		Framebuffers.clear();

		CurrentWidth = fb->GetClientWidth();
		CurrentHeight = fb->GetClientHeight();
		CurrentVSync = fb->GetVSync();
		CurrentHdr = vk_hdr;
		CurrentExclusiveFullscreen = exclusiveFullscreen;

		SwapChain->Create(CurrentWidth, CurrentHeight, CurrentVSync ? 2 : 3, CurrentVSync, CurrentHdr, CurrentExclusiveFullscreen);

		RenderFinishedSemaphores.clear();
		for (int i = 0; i < SwapChain->ImageCount(); i++)
		{
			RenderFinishedSemaphores.push_back(SemaphoreBuilder()
				.DebugName("RenderFinishedSemaphore")
				.Create(fb->device.get()));
		}
	}

	PresentImageIndex = SwapChain->AcquireImage(SwapChainImageAvailableSemaphore.get());
	if (PresentImageIndex != -1)
	{
#ifdef __ANDROID__
		I_AndroidVulkanStarted();
#endif
		fb->GetPostprocess()->DrawPresentTexture(fb->mOutputLetterbox, true, false);
	}
}

void VkFramebufferManager::QueuePresent()
{
	if (PresentImageIndex != -1)
		SwapChain->QueuePresent(PresentImageIndex, RenderFinishedSemaphores[PresentImageIndex].get());
}
