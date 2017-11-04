/*!
 * \author ddubois 
 * \date 21-Oct-17.
 */

#ifndef RENDERER_RENDER_PASS_MANAGER_H
#define RENDERER_RENDER_PASS_MANAGER_H

#include <glm/glm.hpp>

namespace nova {
    class shaderpack;
    class renderpass;

    /*!
     * \brief Holds all the render passes that we made from the loaded shaderpack
     *
     * Nova explicitly defines a few renderpasses:
     *  - Virtual texture pass to see what textures are needed
     *  - shadows
     *  - Gbuffers and lighting
     *      - Two options for the pass:
     *          - if the shaderpack uses deferres passes, opaques in one subpass, then block lights, then deferred
     *          passes, then transparents
     *          - If the shaderpack does not use deferred passes, opaques in one subpass, then transparents, then block l
     *          ights
     *      - Might want to use subpasses for the composites as well
     *  - Final
     *
     * each of those is split into subpases based on render type (fully opaque, cutout, transparent, etc)
     */
    class renderpass_manager {
    public:
        explicit renderpass_manager(std::shared_ptr<shaderpack> shaders);

        /*!
         * \brief Rebuilds the entire renderpasses
         *
         * Renderpasses are dependent on the framebuffer size so there we go
         *
         * And since there's new renderpasses we'll need to rebuild our pipelines as well...
         *
         * \param window_size
         */
        void rebuild_all(vk::Extent2D& window_size);

        std::shared_ptr<renderpass> get_main_renderpass();
        std::shared_ptr<renderpass> get_final_renderpass();

    private:
        void create_final_renderpass(vk::Extent2D& window_size);

        void create_main_renderpass(vk::Extent2D& window_size);

        std::shared_ptr<renderpass> final_renderpass;
        std::shared_ptr<renderpass> main_renderpass;
    };
}

#endif //RENDERER_RENDER_PASS_MANAGER_H