//
// Created by David on 15-Apr-16.
//

#ifndef RENDERER_GLFW_GL_WINDOW_H
#define RENDERER_GLFW_GL_WINDOW_H

#define GLFW_VULKAN_SUPPORT

#include <glad/glad.h>
#include <json.hpp>
#include "GLFW/glfw3.h"
#include "../../data_loading/settings.h"
#include <RenderDocManager.h>
#include <glm/glm.hpp>

namespace nova {
    struct window_parameters {
        int xPos;
        int yPos;
        int width;
        int height;
    };
    
    /*!
     * \brief Represents a GLFW window with an OpenGL context
     *
     * In OpenGL, the window is responsible for maintaining the OpenGL context, or the context has to be bound to a window,
     * or something. I'm not entirely sure, but I do know that the window and OpenGL context are pretty closely coupled.
     *
     * Point is, this class is pretty important and you shouldn't leave home without it
     */
    class glfw_vk_window : public iconfig_listener {
    public:
        /*!
         * \brief Creates a window and a corresponding OpenGL context
         */
        glfw_vk_window();

        ~glfw_vk_window();

        /**
         * iwindow methods
         */

        virtual int init();

        virtual void destroy();

        virtual void end_frame();

        virtual void set_fullscreen(bool fullscreen);

        virtual glm::vec2 get_size();

        virtual bool should_close();

        bool is_active();

        void set_mouse_grabbed(bool grabbed);

        /**
         * iconfig_change_listener methods
         */

        void on_config_change(nlohmann::json &new_config) override;

        void on_config_loaded(nlohmann::json &config) override;

        static void setActive(bool active);

        const char** get_required_extensions(uint32_t* count) const;

    private:
        static bool active;
        GLFWwindow *window;
        glm::ivec2 window_dimensions;
        std::unique_ptr<RenderDocManager> renderdoc_manager;
        struct window_parameters windowed_window_parameters;
        void set_framebuffer_size(glm::ivec2 new_framebuffer_size);
    };
}


#endif //RENDERER_GLFW_GL_WINDOW_H